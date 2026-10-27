"""Train observed biomass baselines and CatBoost without using the test season to select."""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from catboost import CatBoostRegressor
from sklearn.dummy import DummyRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import PolynomialFeatures, StandardScaler

from .evaluate import evaluate_predictions, write_report
from .splits import TARGET, apply_splits, freeze_splits, validate_observed
from .common import FEATURE_NAMES, ML_ROOT

MODEL_VERSION = "wheat-biomass-v1"


def validate_features(frame: pd.DataFrame) -> str:
    validate_observed(frame)
    missing = set(FEATURE_NAMES + ["feature_profile", "latitude", "longitude"]) - set(frame)
    if missing:
        raise ValueError(f"Missing feature columns: {sorted(missing)}")
    values = frame[FEATURE_NAMES + ["latitude", "longitude"]].to_numpy(dtype=float)
    if not np.isfinite(values).all():
        raise ValueError("Training features and coordinates must be finite")
    if (frame.days_since_sowing <= 0).any() or (frame.days_since_sowing % 1 != 0).any():
        raise ValueError("Planting age must be a positive integer")
    if not frame.latitude.between(-90, 90).all() or not frame.longitude.between(-180, 180).all():
        raise ValueError("Invalid geographic coordinates")
    for name in FEATURE_NAMES:
        if name.startswith("soil_moisture_") and not frame[name].between(0, 1).all():
            raise ValueError("Soil moisture must be volumetric fraction between 0 and 1")
        if name.startswith("vpd_") and (frame[name] < 0).any():
            raise ValueError("VPD cannot be negative")
        if name.startswith("sunshine_") and not frame[name].between(0, 24).all():
            raise ValueError("Daily sunshine hours must be between 0 and 24")
        if name.startswith("temperature_") and (frame[name] <= -273.15).any():
            raise ValueError("Temperature must exceed absolute zero")
    profiles = frame.feature_profile.unique().tolist()
    if len(profiles) != 1 or profiles[0] not in {"ifs-soil-0-7-v1", "era5-soil-0-7-v1"}:
        raise ValueError("Exactly one recognized weather profile is required")
    if "sowing_date" in frame:
        age = (pd.to_datetime(frame.target_date) - pd.to_datetime(frame.sowing_date)).dt.days
        if not np.array_equal(age.to_numpy(), frame.days_since_sowing.to_numpy()):
            raise ValueError("Planting age disagrees with recorded dates")
    return profiles[0]


def candidate_models(seed: int = 42, iterations: int = 200) -> dict:
    # Fixed, deliberately small candidates: no hyperparameter search on the holdout.
    # CatBoost parameters: https://catboost.ai/docs/en/concepts/python-reference_catboostregressor
    return {
        "median": DummyRegressor(strategy="median"),
        "age_only": make_pipeline(PolynomialFeatures(degree=2, include_bias=False),
                                  StandardScaler(), Ridge(alpha=10.0)),
        "catboost": CatBoostRegressor(iterations=iterations, depth=3, learning_rate=0.05,
                                      loss_function="RMSE", l2_leaf_reg=5.0,
                                      random_seed=seed, thread_count=2, verbose=False,
                                      allow_writing_files=False),
    }


def columns_for(model_name: str) -> list[str]:
    return FEATURE_NAMES if model_name == "catboost" else ["days_since_sowing"]


def predict_candidate(model, model_name: str, frame: pd.DataFrame) -> np.ndarray:
    # Biomass cannot be negative; use the same declared clipping for every candidate.
    return np.maximum(np.asarray(model.predict(frame[columns_for(model_name)]), dtype=float), 0.0)


def select_candidate(validation_metrics: dict) -> str:
    """This function deliberately accepts validation metrics only, never test metrics."""
    priority = {"median": 0, "age_only": 1, "catboost": 2}
    return min(validation_metrics, key=lambda name: (validation_metrics[name]["mae_g_m2"], priority[name]))


def frame_hash(frame: pd.DataFrame) -> str:
    stable = frame.sort_values("visit_id").sort_index(axis=1).to_csv(index=False)
    return hashlib.sha256(stable.encode("utf-8")).hexdigest()


def train_models(frame: pd.DataFrame, split_path: str | Path, artifact_dir: str | Path,
                 report_dir: str | Path, seed: int = 42, iterations: int = 200) -> dict:
    profile = validate_features(frame)
    manifest = freeze_splits(frame, split_path, seed)
    split = apply_splits(frame, manifest)
    subsets = {part: split[split.partition.eq(part)].copy() for part in ("train", "validation", "test")}
    models = candidate_models(seed, iterations)
    validation = {}
    for name, model in models.items():
        model.fit(subsets["train"][columns_for(name)], subsets["train"][TARGET])
        validation[name] = evaluate_predictions(subsets["validation"],
                                                 predict_candidate(model, name, subsets["validation"]))
    selected = select_candidate(validation)
    # Freeze selection before accessing any test outcome. Do not refit after reporting validation.
    results, prediction_tables = {}, []
    for name, model in models.items():
        results[name] = {"validation": validation[name]}
        for partition, subset in subsets.items():
            prediction = predict_candidate(model, name, subset)
            if partition != "validation":
                results[name][partition] = evaluate_predictions(subset, prediction)
            rows = subset.copy()
            rows["model"] = name
            rows["prediction_g_m2"] = prediction
            prediction_tables.append(rows)
    weather_improves = results["catboost"]["test"]["mae_g_m2"] < results["age_only"]["test"]["mae_g_m2"]
    minimum_age = float(subsets["train"].days_since_sowing.min())
    maximum_age = float(subsets["train"].days_since_sowing.max())
    supported_test = subsets["test"][subsets["test"].days_since_sowing.between(minimum_age, maximum_age)]
    age_support = {
        "training_age_min": minimum_age, "training_age_max": maximum_age,
        "test_in_age_range": len(supported_test),
        "test_outside_age_range": len(subsets["test"]) - len(supported_test),
        "within_age_range_metrics": {
            name: evaluate_predictions(supported_test, predict_candidate(model, name, supported_test))
            for name, model in models.items()} if not supported_test.empty else {},
        "interpretation": "Additional subgroup analysis only; all test rows remain in primary metrics. Age support does not prove geographic or weather support.",
    }
    limits = [
        "One Bulgarian winter-wheat region; this is not validation across regions or varieties.",
        "Adjacent fields can share weather-grid cells, dates and management; rows are not independent farms.",
        "The temporal holdout covers November and April only, not late-season performance.",
        "Biomass is not a health percentage; unobserved soil, pests and management affect growth.",
        "Historical modelled weather is not a field sensor or proof of issue-time availability.",
        "No calibrated confidence percentage or prediction interval is available.",
    ]
    if not weather_improves:
        limits.append("Weather did not improve holdout MAE over planting age alone; do not claim weather adds predictive value.")
    if age_support["test_outside_age_range"]:
        limits.append("Some temporal holdout observations are outside the training planting-age range; primary errors retain them and age-supported subgroup errors are reported separately.")
    grid_columns = ["weather_grid_latitude", "weather_grid_longitude", "target_date"]
    grid_available = set(grid_columns).issubset(frame.columns) and not frame[grid_columns].isna().any().any()
    weather_independence = None
    if grid_available:
        grid_sets = {part: set(group[grid_columns].itertuples(index=False, name=None)) for part, group in subsets.items()}
        shared = len(grid_sets["train"] & grid_sets["validation"])
        weather_independence = {"provider_grid_cells": len(frame[grid_columns[:2]].drop_duplicates()),
                                "provider_grid_date_groups": len(frame[grid_columns].drop_duplicates()),
                                "train_validation_shared_grid_dates": shared}
        if shared:
            limits.append(f"Train and validation share {shared} provider-grid/date groups; grouped field splitting does not ensure independent weather. Frozen splits were not changed after evaluation.")
    evaluation = {
        "schema_version": "wheat-evaluation-v1", "selected_model": selected,
        "selection_rule": "minimum first-season grouped validation MAE; simpler model wins exact ties",
        "weather_holdout_gate": "PASS_EXPLORATORY" if weather_improves else "FAIL",
        "age_support": age_support,
        "models": results, "partitions": {
            part: {"observations": len(group), "field_seasons": int(group.field_season_id.nunique()),
                   "sampling_dates": int(group.target_date.nunique()),
                   "weather_request_windows": int(group.weather_window_id.nunique()) if "weather_window_id" in group else None,
                   "weather_grid_date_groups": len(group[grid_columns].drop_duplicates()) if grid_available else None}
            for part, group in subsets.items()},
        "feature_profile": profile, "feature_names": FEATURE_NAMES,
        "configuration": {"seed": seed, "catboost_iterations": iterations, "catboost_depth": 3,
                          "catboost_learning_rate": 0.05, "catboost_l2_leaf_reg": 5.0,
                          "age_only_polynomial_degree": 2, "age_only_ridge_alpha": 10.0,
                          "prediction_clip_min": 0.0, "refit_on_validation": False},
        "data_sha256": frame_hash(frame), "split_fingerprint": manifest["dataset_fingerprint"],
        "limitations": limits,
    }
    if weather_independence is not None:
        evaluation["weather_independence"] = weather_independence
    artifact_dir, report_dir = Path(artifact_dir), Path(report_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)
    models["catboost"].save_model(str(artifact_dir / "model.cbm"))
    for name in ("median", "age_only"):
        joblib.dump(models[name], artifact_dir / f"{name}.joblib")
    chosen_file = "model.cbm" if selected == "catboost" else f"{selected}.joblib"
    selected_metadata = {
        "model_version": MODEL_VERSION, "selected_model": selected, "model_file": chosen_file,
        "model_format": "catboost" if selected == "catboost" else "joblib",
        "input_features": columns_for(selected), "clip_min": 0.0, "feature_profile": profile,
        "artifact_sha256": hashlib.sha256((artifact_dir / chosen_file).read_bytes()).hexdigest(),
    }
    train = subsets["train"]
    schema = {
        "schema_version": "wheat-features-v1", "feature_names": FEATURE_NAMES,
        "feature_profile": profile, "target": TARGET, "unit": "g/m2",
        "training_ranges": {name: {"min": float(train[name].min()), "max": float(train[name].max())}
                            for name in FEATURE_NAMES},
        "scope": {"crop": "winter_wheat", "latitude_min": float(train.latitude.min()),
                  "latitude_max": float(train.latitude.max()), "longitude_min": float(train.longitude.min()),
                  "longitude_max": float(train.longitude.max())},
    }
    card = {
        "model_version": MODEL_VERSION, "status": "EXPERIMENTAL", "kind": "DRY_ABOVEGROUND_BIOMASS",
        "selected_model": selected, "feature_profile": profile, "unit": "g/m2", "interval": None,
        "confidence": None, "weather_holdout_gate": evaluation["weather_holdout_gate"],
        "weather_claim_supported": bool(weather_improves),
        "training_data_sha256": evaluation["data_sha256"],
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "selection_rule": evaluation["selection_rule"], "partitions": evaluation["partitions"],
        "age_support": age_support,
        "weather_independence": weather_independence,
        "source_urls": ["https://zenodo.org/records/17475742", "https://open-meteo.com/en/docs/historical-weather-api"],
        "limitations": limits,
    }
    for filename, payload in (("selected_model.json", selected_metadata), ("feature_schema.json", schema), ("model_card.json", card)):
        (artifact_dir / filename).write_text(json.dumps(payload, indent=2, allow_nan=False), encoding="utf-8")
    pd.concat(prediction_tables, ignore_index=True).to_csv(report_dir / "predictions.csv", index=False)
    write_report(evaluation, report_dir)
    return evaluation


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--features", type=Path, default=ML_ROOT / "data/processed/features.csv")
    parser.add_argument("--splits", type=Path, default=ML_ROOT / "data/splits/wheat_v1.json")
    parser.add_argument("--artifacts", type=Path, default=ML_ROOT / "artifacts/wheat_biomass_v1")
    parser.add_argument("--reports", type=Path, default=ML_ROOT / "reports/wheat_v1")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    result = train_models(pd.read_csv(args.features), args.splits, args.artifacts, args.reports, args.seed)
    print(json.dumps({"selected_model": result["selected_model"], "weather_holdout_gate": result["weather_holdout_gate"],
                      "reports": str(args.reports)}, indent=2))


if __name__ == "__main__":
    main()
