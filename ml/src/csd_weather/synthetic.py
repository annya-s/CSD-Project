"""Generate labelled engineering weather scenarios, never fabricated observed biomass."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
import pandas as pd

from .features import build_features
from .splits import TARGET, apply_splits

ML_ROOT = Path(__file__).resolve().parents[2]


def saturation_vapour_pressure(temperature_c: np.ndarray) -> np.ndarray:
    # FAO-56 eq. 11: https://www.fao.org/4/x0490e/x0490e07.htm . Result: kPa.
    return 0.6108 * np.exp(17.27 * temperature_c / (temperature_c + 237.3))


def check_physical_history(history: dict) -> None:
    hourly, daily = history["response"]["hourly"], history["response"]["daily"]
    expected = len(hourly["time"])
    for name in ("temperature_2m", "relative_humidity_2m", "vapour_pressure_deficit", "soil_moisture_0_to_7cm"):
        values = np.asarray(hourly[name], dtype=float)

        if len(values) != expected or not np.isfinite(values).all():
            raise ValueError(f"Invalid auxiliary weather array: {name}")
        if name == "relative_humidity_2m" and ((values < 0) | (values > 100)).any():
            raise ValueError("Relative humidity out of physical bounds")
        if name == "soil_moisture_0_to_7cm" and ((values < 0) | (values > 1)).any():
            raise ValueError("Soil VWC out of physical bounds")
        if name == "vapour_pressure_deficit" and (values < 0).any():
            raise ValueError("Negative VPD")
        if name == "temperature_2m" and (values <= -273.15).any():
            raise ValueError("Temperature below absolute zero")
        
    sunshine = np.asarray(daily["sunshine_duration"], dtype=float)
    daylight = np.asarray(daily["daylight_duration"], dtype=float)
    
    if len(sunshine) != len(daylight) or len(sunshine) != len(daily["time"]):
        raise ValueError("Misaligned daily sunshine/daylight")
    if not (np.isfinite(sunshine).all() and np.isfinite(daylight).all()):
        raise ValueError("Nonfinite sunshine/daylight")
    if ((sunshine < 0) | (sunshine > daylight) | (daylight > 86400) | (daylight < 0)).any():
        raise ValueError("Sunshine exceeds daylight or physical bounds")


def generate_scenarios(frame: pd.DataFrame, splits: dict, windows: dict, config: dict) -> tuple[list[dict], dict]:
    assigned = apply_splits(frame, splits)
    train = assigned[assigned.partition.eq("train")].sort_values("visit_id")
    if train.empty:
        raise ValueError("No training seed windows")
    seed = int(config["seed"])
    rng = np.random.default_rng(seed)
    eligible = []
    excluded = []
    for row in train.to_dict("records"):
        visit_id = str(row["visit_id"])
        if visit_id not in windows:
            excluded.append({"visit_id": visit_id, "reason": "No history window"})
            continue
        try:
            history = windows[visit_id]
            if history["profile"] != row["feature_profile"]:
                raise ValueError("History profile differs from observed feature profile")
            check_physical_history(history)
            build_features(history, row["target_date"], row["sowing_date"], profile=history["profile"])
        except (KeyError, ValueError, TypeError) as exc:
            excluded.append({"visit_id": visit_id, "reason": str(exc)})
            continue
        eligible.append(row)
    if not eligible:
        raise ValueError("No valid training-only history windows for synthetic tests")
    counts = config["counts"]
    if set(counts) != {"resampled", "perturbed", "invalid"} or any(type(count) is not int or count < 0 for count in counts.values()):
        raise ValueError("Scenario counts must be nonnegative integers for the three families")
    scenarios = []
    for family in ("resampled", "perturbed", "invalid"):
        for index in range(counts[family]):
            row = eligible[int(rng.integers(len(eligible)))]
            disturbance = None
            subtype = family
            if family == "perturbed" and index % 4 == 3:
                # Select a whole observed wet/dry window, keeping its own planting context and season.
                sorted_rows = sorted(eligible, key=lambda item: float(np.mean(
                    windows[str(item["visit_id"])]["response"]["hourly"]["soil_moisture_0_to_7cm"])))
                row = sorted_rows[0 if (index // 4) % 2 == 0 else -1]
                subtype = "observed_dry_window" if (index // 4) % 2 == 0 else "observed_wet_window"
            history = copy.deepcopy(windows[str(row["visit_id"])])
            hourly, daily = history["response"]["hourly"], history["response"]["daily"]
            if family == "perturbed" and index % 4 in (0, 1):
                sign = 1 if index % 4 == 0 else -1
                disturbance = sign * float(rng.uniform(*config["temperature_offset_c"]))
                temperature = np.asarray(hourly["temperature_2m"], dtype=float) + disturbance
                rh = np.asarray(hourly["relative_humidity_2m"], dtype=float)
                hourly["temperature_2m"] = temperature.tolist()
                hourly["vapour_pressure_deficit"] = (saturation_vapour_pressure(temperature) * (1 - rh / 100)).tolist()
                subtype = "warmer_at_same_rh" if sign == 1 else "cooler_at_same_rh"
            elif family == "perturbed" and index % 4 == 2:
                disturbance = float(rng.uniform(*config["light_multiplier"]))
                daily["sunshine_duration"] = (np.asarray(daily["sunshine_duration"]) * disturbance).tolist()
                if "sunshine_duration" in hourly:
                    hourly["sunshine_duration"] = (np.asarray(hourly["sunshine_duration"]) * disturbance).tolist()
                subtype = "reduced_sunshine"
            expected_rejection = None
            if family == "invalid":
                invalid_type = index % 4
                if invalid_type == 0:
                    for values in hourly.values():
                        if isinstance(values, list):
                            del values[0]
                    subtype, expected_rejection = "missing_hour", "Incomplete hourly coverage"
                elif invalid_type == 1:
                    history["response"]["hourly_units"]["soil_moisture_0_to_7cm"] = "%"
                    subtype, expected_rejection = "wrong_soil_unit", "Wrong soil-moisture units"
                elif invalid_type == 2:
                    hourly["time"][1] = hourly["time"][0]
                    subtype, expected_rejection = "duplicate_hour", "Duplicate hourly timestamp"
                else:
                    daily["sunshine_duration"][0] = daily["daylight_duration"][0] + 3600
                    subtype, expected_rejection = "impossible_sunshine", "Sunshine exceeds daylight"
            # This added metadata prevents a modified history from masquerading as an original provider response.
            history["data_origin"] = "SYNTHETIC_SCENARIO"
            history["scenario_family"] = subtype
            validation_error = None
            features = None
            try:
                check_physical_history(history)
                features = build_features(history, row["target_date"], row["sowing_date"], profile=history["profile"])
            except (KeyError, ValueError, TypeError) as exc:
                validation_error = str(exc)
            if family != "invalid" and validation_error:
                raise ValueError(f"Generated valid scenario failed checks: {subtype}: {validation_error}")
            if family == "invalid" and validation_error is None:
                raise ValueError(f"Invalid fixture unexpectedly passed: {subtype}")
            scenarios.append({
                "scenario_id": f"scenario-{len(scenarios):04d}", "data_origin": "SYNTHETIC_SCENARIO",
                "scenario_group": family, "scenario_family": subtype,
                "parent_visit_id": str(row["visit_id"]), "parent_window_id": str(row.get("weather_window_id", row["visit_id"])),
                "parent_field_season_id": row["field_season_id"], "parent_partition": "train",
                "target_date": row["target_date"], "sowing_date": row["sowing_date"],
                TARGET: None, "generator_version": config["generator_version"], "seed": seed,
                "allowed_use": "PIPELINE_TEST" if family == "invalid" else "ROBUSTNESS_TEST",
                "source_urls": config["source_urls"], "disturbance": disturbance,
                "expected_rejection": expected_rejection, "validation_error": validation_error,
                "validation_passed": validation_error is None, "features": features, "history": history,
            })
    manifest = {"generator_version": config["generator_version"], "seed": seed,
                "counts": counts, "total": len(scenarios), "eligible_training_seeds": len(eligible),
                "validation_passed": sum(item["validation_passed"] for item in scenarios),
                "validation_rejected": sum(not item["validation_passed"] for item in scenarios),
                "unique_parent_visits": len({item["parent_visit_id"] for item in scenarios}),
                "unique_parent_windows": len({item["parent_window_id"] for item in scenarios}),
                "excluded_seeds": excluded, "source_urls": config["source_urls"],
                "config_sha256": hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest(),
                "split_fingerprint": splits["dataset_fingerprint"],
                "target_policy": "All biomass labels are null. Not permitted in real model fitting or evaluation.",
                "limitations": ["Resampled windows are not new independent observations.",
                                "Perturbations are engineering tests, not calibrated crop or hydrological simulations."]}
    return scenarios, manifest


def write_scenarios(scenarios: list[dict], manifest: dict, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    with (output / "weather_scenarios.jsonl").open("w", encoding="utf-8") as handle:
        for scenario in scenarios:
            handle.write(json.dumps(scenario, allow_nan=False, separators=(",", ":")) + "\n")
    summary = []
    for item in scenarios:
        row = {name: value for name, value in item.items() if name not in {"history", "features", "source_urls"}}
        row["source_urls"] = json.dumps(item["source_urls"])
        row.update(item["features"] or {})
        summary.append(row)
    pd.DataFrame(summary).to_csv(output / "weather_scenarios.csv", index=False)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, allow_nan=False), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--features", type=Path, default=ML_ROOT / "data/processed/features.csv")
    parser.add_argument("--splits", type=Path, default=ML_ROOT / "data/splits/wheat_v1.json")
    parser.add_argument("--windows", type=Path, default=ML_ROOT / "data/processed/weather_windows.json")
    parser.add_argument("--config", type=Path, default=ML_ROOT / "config/synthetic_scenarios_v1.json")
    parser.add_argument("--output", type=Path, default=ML_ROOT / "data/synthetic")
    args = parser.parse_args()
    scenarios, manifest = generate_scenarios(pd.read_csv(args.features), json.loads(args.splits.read_text(encoding="utf-8")),
                                             json.loads(args.windows.read_text(encoding="utf-8")),
                                             json.loads(args.config.read_text(encoding="utf-8")))
    write_scenarios(scenarios, manifest, args.output)
    print(json.dumps({"scenarios": len(scenarios), "output": str(args.output), "counts": manifest["counts"]}))


if __name__ == "__main__":
    main()
