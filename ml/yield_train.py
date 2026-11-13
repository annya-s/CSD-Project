"""Train and compare three yield models on ml/data/yield_training.csv.

Usage (from the project root): ml\\.venv\\Scripts\\python.exe ml\\yield_train.py

Target: yield ratio (actual yield / country trend). Features: crop plus the four
season weather averages and absolute latitude (see yield_dataset.py).

Evaluation is honest about time: the years 2022-2024 are held out as a test set and
never used for fitting or model choice. Model choice uses cross-validation grouped by
year on 2011-2021, so a model is never tested on a year it saw during fitting.
Every model is compared with a no-skill baseline that predicts a normal year (ratio 1).
"""
import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from catboost import CatBoostRegressor
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import GroupKFold
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import OneHotEncoder, PolynomialFeatures, StandardScaler

from yield_dataset import FEATURES, OUTPUT as DATA

ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "model/yield"
TEST_YEARS = {2022, 2023, 2024}
WEATHER = ["temperature", "soil_moisture", "vpd", "sunshine_hours"]
# The label is an anomaly (this year vs normal), so the model also sees weather anomalies:
# this season minus that location's usual season.
ANOMALIES = [f"{name}_anomaly" for name in WEATHER]
NUMERIC = FEATURES + ANOMALIES
COLUMNS = ["crop"] + NUMERIC


def climatology(frame):
    """Each location's usual season weather (mean over the given years)."""
    return frame.groupby("location")[WEATHER].mean()


def add_anomalies(frame, climate):
    usual = climate.loc[frame.location].to_numpy()
    return frame.assign(**{f"{name}_anomaly": frame[name].to_numpy() - usual[:, i]
                           for i, name in enumerate(WEATHER)})


def models():
    numeric = make_pipeline(PolynomialFeatures(2, include_bias=False), StandardScaler())
    crop = OneHotEncoder(handle_unknown="ignore")
    return {
        # Squared terms let a linear model express "too hot" and "too cold" both hurting.
        "ridge": make_pipeline(ColumnTransformer([("crop", crop, ["crop"]),
                                                  ("weather", numeric, NUMERIC)]), Ridge(alpha=10)),
        "random_forest": make_pipeline(
            ColumnTransformer([("crop", crop, ["crop"])], remainder="passthrough"),
            RandomForestRegressor(n_estimators=300, min_samples_leaf=15, max_features=0.5,
                                  random_state=42, n_jobs=2)),
        "catboost": CatBoostRegressor(cat_features=["crop"], iterations=400, depth=4, learning_rate=0.05, l2_leaf_reg=10,
                                     loss_function="MAE", random_seed=42, verbose=False,
                                     allow_writing_files=False, thread_count=2),
    }


def scores(truth, predicted):
    return dict(mae_pct=round(100 * mean_absolute_error(truth, predicted), 2),
                r2=round(float(r2_score(truth, predicted)), 3))


def main():
    frame = pd.read_csv(DATA)
    # Ratios outside 0.4-1.6 are mostly reporting breaks (area changes, data revisions), not weather.
    frame = frame[frame.yield_ratio.between(0.4, 1.6)].reset_index(drop=True)
    train, test = frame[~frame.year.isin(TEST_YEARS)], frame[frame.year.isin(TEST_YEARS)]
    # Usual climate from training years only, so the test years stay unseen.
    climate = climatology(train)
    train, test = add_anomalies(train, climate), add_anomalies(test, climate)
    results = {"baseline_normal_year": dict(validation=scores(train.yield_ratio, np.ones(len(train))),
                                            test=scores(test.yield_ratio, np.ones(len(test))))}
    fitted = {}
    for name, model in models().items():
        predictions = np.zeros(len(train))
        for fit_rows, check_rows in GroupKFold(n_splits=5).split(train, groups=train.year):
            model.fit(train.iloc[fit_rows][COLUMNS], train.iloc[fit_rows].yield_ratio)
            predictions[check_rows] = model.predict(train.iloc[check_rows][COLUMNS])
        model.fit(train[COLUMNS], train.yield_ratio)
        results[name] = dict(validation=scores(train.yield_ratio, predictions),
                             test=scores(test.yield_ratio, model.predict(test[COLUMNS])))
        fitted[name] = model
        print(f"{name:22s} {results[name]}", flush=True)
    print(f"{'baseline_normal_year':22s} {results['baseline_normal_year']}")

    # Choose on validation only; the test years stay a fair final check.
    selected = min(fitted, key=lambda name: results[name]["validation"]["mae_pct"])
    climate = climatology(frame)  # the app uses all years' climate and model
    frame = add_anomalies(frame, climate)
    final = models()[selected].fit(frame[COLUMNS], frame.yield_ratio)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(final, MODEL_DIR / "model.joblib")
    normals = json.loads((ROOT / "data/yield_normals.json").read_text(encoding="utf-8"))
    (MODEL_DIR / "normals.json").write_text(json.dumps(normals, indent=1), encoding="utf-8")
    metadata = dict(
        model_version="yield-ratio-v2", selected_model=selected, target="yield_ratio",
        features=COLUMNS, crops=sorted(frame.crop.unique()),
        climatology={loc: {k: round(float(v), 4) for k, v in row.items()}
                     for loc, row in climate.iterrows()},
        training_ranges={c: dict(min=round(float(frame[c].min()), 3), max=round(float(frame[c].max()), 3))
                         for c in WEATHER},
        rows=len(frame), test_years=sorted(TEST_YEARS), locations=int(frame.location.nunique()),
        years=[int(frame.year.min()), int(frame.year.max())], metrics=results,
        data_sha256=hashlib.sha256(DATA.read_bytes()).hexdigest(),
        sources=["FAOSTAT Crops and livestock products (FAO, CC BY 4.0)",
                 "Open-Meteo historical weather API (ERA5 reanalysis)"],
        limitations=["Country-level yields paired with weather at one representative point per country.",
                     "Six-month hemisphere-based season, not each crop's real calendar.",
                     "Soil moisture is 0-7 cm in training; the app supplies 3-9 cm.",
                     "In the app, the last 14 days stand in for a whole season."])
    (MODEL_DIR / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(f"Selected {selected}; saved to {MODEL_DIR}")


if __name__ == "__main__":
    main()
