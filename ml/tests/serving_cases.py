"""Small deterministic fixtures; never use downloaded records or network calls."""
import hashlib
import json
from datetime import date, datetime, timedelta, timezone

import joblib
import pandas as pd
from sklearn.dummy import DummyRegressor

from csd_weather.common import FEATURE_NAMES, PROFILE
from csd_weather.features import DAILY_UNITS, HOURLY_UNITS, build_features


def request_payload():
    target = date(2023, 5, 15)
    start = datetime.combine(target - timedelta(days=14), datetime.min.time(), timezone.utc)
    hours = [int(start.timestamp()) + i * 3600 for i in range(336)]
    history = {
        "schema_version": "weather-history-v1", "profile": PROFILE,
        "timezone": "UTC", "latitude": 52.0, "longitude": 13.0,
        "response": {
            "hourly_units": HOURLY_UNITS, "daily_units": DAILY_UNITS,
            "hourly": {"time": hours, "temperature_2m": [15.0] * 336,
                       "relative_humidity_2m": [60.0] * 336,
                       "vapour_pressure_deficit": [0.7] * 336,
                       "soil_moisture_0_to_7cm": [0.25] * 336,
                       "sunshine_duration": [600.0] * 336},
            "daily": {"time": hours[::24], "sunshine_duration": [14400.0] * 14,
                      "daylight_duration": [50000.0] * 14},
        },
    }
    return {"crop": "winter_wheat", "growing_system": "outdoor_soil", "sowing_date": "2022-10-01",
            "prediction_date": str(target), "latitude": 52.0, "longitude": 13.0, "history": history}


def artifacts(directory):
    directory.mkdir()
    payload = request_payload()
    features = build_features(payload["history"], payload["prediction_date"], payload["sowing_date"])
    model = DummyRegressor(strategy="constant", constant=250).fit(pd.DataFrame([features]), [250])
    path = directory / "median.joblib"
    joblib.dump(model, path)
    selection = {"model_version": "test-v1", "selected_model": "median", "model_file": path.name,
                 "model_format": "joblib", "input_features": FEATURE_NAMES, "clip_min": 0,
                 "feature_profile": PROFILE, "artifact_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    schema = {"schema_version": "wheat-features-v1", "feature_names": FEATURE_NAMES, "feature_profile": PROFILE,
              "unit": "g/m2", "training_ranges": {name: {"min": value - 1, "max": value + 1} for name, value in features.items()},
              "scope": {"crop": "winter_wheat", "latitude_min": 51, "latitude_max": 53,
                        "longitude_min": 12, "longitude_max": 14}}
    card = {"model_version": "test-v1", "selected_model": "median", "feature_profile": PROFILE,
            "unit": "g/m2", "kind": "DRY_ABOVEGROUND_BIOMASS", "weather_holdout_gate": "FAIL"}
    for name, value in [("selected_model", selection), ("feature_schema", schema), ("model_card", card)]:
        (directory / f"{name}.json").write_text(json.dumps(value))
    return directory
