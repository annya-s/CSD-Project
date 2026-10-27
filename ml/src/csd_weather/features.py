"""One strict feature builder used by training, tests and prediction."""
from __future__ import annotations

import argparse
import math
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
import pandas as pd

from .common import FEATURE_NAMES, ML_ROOT, PROFILE, read_json, write_json

HOURLY_UNITS = {"time": "unixtime", "temperature_2m": "°C", "relative_humidity_2m": "%",
                "vapour_pressure_deficit": "kPa", "soil_moisture_0_to_7cm": "m³/m³", "sunshine_duration": "s"}
DAILY_UNITS = {"time": "unixtime", "sunshine_duration": "s", "daylight_duration": "s"}


def as_date(value: str | date) -> date:
    return value if isinstance(value, date) and not isinstance(value, datetime) else date.fromisoformat(str(value))


def local_midnight(day: date, zone: ZoneInfo) -> int:
    return int(datetime.combine(day, time.min, zone).timestamp())


def build_features(history: dict, prediction_date: str | date, sowing_date: str | date,
                   profile: str = PROFILE) -> dict:
    target, sowing = as_date(prediction_date), as_date(sowing_date)
    age = (target - sowing).days
    if age <= 0:
        raise ValueError("INVALID_PLANTING_AGE")
    if history.get("schema_version") != "weather-history-v1" or history.get("profile") != profile:
        raise ValueError("INCOMPATIBLE_PROFILE")
    if profile not in {"ifs-soil-0-7-v1", "era5-soil-0-7-v1"}:
        raise ValueError("UNSUPPORTED_PROFILE")
    zone = ZoneInfo(history["timezone"])
    response = history["response"]
    if response.get("hourly_units") != HOURLY_UNITS or response.get("daily_units") != DAILY_UNITS:
        raise ValueError("INCOMPATIBLE_UNITS_OR_DEPTH")
    start = target - timedelta(days=14)
    expected_times = list(range(local_midnight(start, zone), local_midnight(target, zone), 3600))
    hourly, daily = response["hourly"], response["daily"]
    if hourly.get("time") != expected_times:
        raise ValueError("INCOMPLETE_OR_MISALIGNED_HOURS")
    expected_days = [start + timedelta(days=i) for i in range(14)]
    if daily.get("time") != [local_midnight(day, zone) for day in expected_days]:
        raise ValueError("INCOMPLETE_OR_MISALIGNED_DAYS")
    arrays = {}
    for key in HOURLY_UNITS:
        if key == "time":
            continue
        values = hourly.get(key)
        if not isinstance(values, list) or len(values) != len(expected_times) or any(x is None for x in values):
            raise ValueError("MISSING_HOURLY_VALUES")
        arr = np.asarray(values, dtype=float)
        if arr.ndim != 1 or not np.isfinite(arr).all():
            raise ValueError("NONFINITE_HOURLY_VALUES")
        arrays[key] = arr
    if ((arrays["relative_humidity_2m"] < 0) | (arrays["relative_humidity_2m"] > 100)).any():
        raise ValueError("INVALID_HUMIDITY")
    if ((arrays["soil_moisture_0_to_7cm"] < 0) | (arrays["soil_moisture_0_to_7cm"] > 1)).any():
        raise ValueError("INVALID_SOIL_MOISTURE")
    if (arrays["vapour_pressure_deficit"] < 0).any():
        raise ValueError("INVALID_VPD")
    if ((arrays["sunshine_duration"] < 0) | (arrays["sunshine_duration"] > 3600)).any():
        raise ValueError("INVALID_HOURLY_SUNSHINE")
    local_dates = np.array([datetime.fromtimestamp(t, timezone.utc).astimezone(zone).date() for t in expected_times])
    values_by_day = {"temperature_mean": [], "vpd_mean": [], "soil_moisture_mean": [], "sunshine_hours_mean": []}
    for i, day in enumerate(expected_days):
        mask = local_dates == day
        for output, column in [("temperature_mean", "temperature_2m"), ("vpd_mean", "vapour_pressure_deficit"),
                               ("soil_moisture_mean", "soil_moisture_0_to_7cm")]:
            values_by_day[output].append(float(arrays[column][mask].mean()))
        try:
            sun = float(daily["sunshine_duration"][i])
            daylight = float(daily["daylight_duration"][i])
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise ValueError("MISSING_DAILY_VALUES") from exc
        if not math.isfinite(sun) or not math.isfinite(daylight) or not 0 <= sun <= daylight + 60 or not 0 <= daylight <= 86400:
            raise ValueError("INVALID_DAILY_SUNSHINE")
        # Minute tolerance covers provider precision / hourly interval allocation near sunrise.
        if abs(sun - arrays["sunshine_duration"][mask].sum()) > 0.1:
            raise ValueError("INCONSISTENT_SUNSHINE")
        values_by_day["sunshine_hours_mean"].append(sun / 3600)
    result = {"days_since_sowing": age}
    for name, values in values_by_day.items():
        for n in (7, 14):
            result[f"{name}_{n}d"] = float(np.mean(values[-n:]))
    return {key: result[key] for key in FEATURE_NAMES}


def create_feature_table(root: Path = ML_ROOT) -> pd.DataFrame:
    processed = root / "data/processed"
    observations = pd.read_csv(processed / "observations.csv")
    windows = read_json(processed / "weather_windows.json")
    rows, rejected = [], []
    for record in observations.to_dict("records"):
        visit = record["visit_id"]
        if visit not in windows:
            rejected.append({"visit_id": visit, "reason": "NO_WEATHER_WINDOW"})
            continue
        history = windows[visit]
        try:
            features = build_features(history, record["target_date"], record["sowing_date"], history["profile"])
            grid = history.get("provider_grid", {})
            rows.append({**record, **features, "feature_profile": history["profile"], "weather_window_id": history["window_id"],
                         "weather_grid_latitude": grid.get("latitude"), "weather_grid_longitude": grid.get("longitude")})
        except ValueError as error:
            rejected.append({"visit_id": visit, "reason": str(error)})
    if not rows:
        raise ValueError("No valid weather feature rows; inspect weather_audit.json")
    frame = pd.DataFrame(rows)
    frame.to_csv(processed / "features.csv", index=False)
    write_json(processed / "feature_rejections.json", rejected)
    write_json(processed / "feature_audit.json", {"observations": len(observations), "feature_rows": len(frame),
               "rejected": len(rejected), "features": FEATURE_NAMES,
               "profiles": sorted(frame.feature_profile.unique().tolist()),
               "weather_windows": int(frame.weather_window_id.nunique()),
               "weather_grid_cells": len(frame[["weather_grid_latitude", "weather_grid_longitude"]].dropna().drop_duplicates()),
               "weather_grid_date_groups": len(frame[["weather_grid_latitude", "weather_grid_longitude", "target_date"]].dropna().drop_duplicates()),
               "hourly_sunshine_aggregated_in_field_timezone": True})
    return frame


def main() -> None:
    argparse.ArgumentParser(description=__doc__).parse_args()
    frame = create_feature_table()
    print(f"Created {len(frame)} feature rows with {len(FEATURE_NAMES)} predictors.")


if __name__ == "__main__":
    main()
