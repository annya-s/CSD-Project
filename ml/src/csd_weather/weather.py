"""Offline utilities for normalizing already-saved weather into IANA local days."""
from __future__ import annotations

import hashlib
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo


from .common import ML_ROOT, PROFILE, read_json
from .features import DAILY_UNITS, HOURLY_UNITS, as_date, local_midnight


def request_params(latitude: float, longitude: float, prediction_date: str | date, model: str = "ecmwf_ifs") -> dict:
    target = as_date(prediction_date)
    # Official API parameters: https://open-meteo.com/en/docs/historical-weather-api
    # UTC plus padding avoids fixed utc_offset_seconds across a DST transition.
    return {"latitude": latitude, "longitude": longitude, "start_date": (target - timedelta(days=15)).isoformat(),
            "end_date": target.isoformat(), "hourly": ",".join(k for k in HOURLY_UNITS if k != "time"),
            "daily": "sunshine_duration,daylight_duration", "models": model, "timezone": "UTC", "timeformat": "unixtime"}


def window_id(params: dict, target: str | date, zone: str) -> str:
    payload = {"params": params, "target": str(target), "field_timezone": zone, "normalization": "iana-hourly-sun-v1"}
    return hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()[:24]


def normalize_response(raw: dict, *, latitude: float, longitude: float, prediction_date: str | date,
                       field_timezone: str, params: dict, retrieved_at: str, profile: str = PROFILE) -> dict:
    target, zone = as_date(prediction_date), ZoneInfo(field_timezone)
    start = target - timedelta(days=14)
    if raw.get("hourly_units") != HOURLY_UNITS or raw.get("daily_units") != DAILY_UNITS:
        raise ValueError("PROVIDER_UNITS_MISMATCH")
    hourly, daily = raw["hourly"], raw["daily"]
    timestamps = hourly["time"]
    if len(timestamps) != len(set(timestamps)) or timestamps != sorted(timestamps):
        raise ValueError("PROVIDER_DUPLICATE_OR_UNSORTED_HOURS")
    if any(len(hourly[key]) != len(timestamps) for key in HOURLY_UNITS):
        raise ValueError("PROVIDER_HOURLY_ARRAY_LENGTH")
    indices = [i for i, stamp in enumerate(timestamps) if local_midnight(start, zone) <= stamp < local_midnight(target, zone)]
    selected = {key: [hourly[key][i] for i in indices] for key in HOURLY_UNITS}
    if any(len(daily[key]) != len(daily["time"]) for key in DAILY_UNITS):
        raise ValueError("PROVIDER_DAILY_ARRAY_LENGTH")
    # Daylight is astronomical for the named calendar date; daily sunshine is NOT reused.
    daylight_by_date = {datetime.fromtimestamp(stamp, timezone.utc).date(): daily["daylight_duration"][i]
                        for i, stamp in enumerate(daily["time"])}
    normalized_daily = {key: [] for key in DAILY_UNITS}
    for offset in range(14):
        day = start + timedelta(days=offset)
        day_indices = [i for i, stamp in enumerate(selected["time"])
                       if datetime.fromtimestamp(stamp, timezone.utc).astimezone(zone).date() == day]
        values = [selected["sunshine_duration"][i] for i in day_indices]
        normalized_daily["time"].append(local_midnight(day, zone))
        normalized_daily["sunshine_duration"].append(None if any(v is None for v in values) else round(sum(values), 5))
        normalized_daily["daylight_duration"].append(daylight_by_date.get(day))
    result = {"schema_version": "weather-history-v1", "profile": profile, "timezone": field_timezone,
              "latitude": float(latitude), "longitude": float(longitude), "retrieved_at": retrieved_at,
              "window_id": window_id(params, target, field_timezone), "request": params,
              "normalization": "UTC instants to IANA local days; daily sunshine summed from hourly seconds; provider astronomical daylight by calendar date",
              "provider_grid": {key: raw.get(key) for key in ("latitude", "longitude", "elevation")},
              "response": {"hourly_units": dict(HOURLY_UNITS), "daily_units": dict(DAILY_UNITS),
                           "hourly": selected, "daily": normalized_daily}}
    return result


def load_cached_history(latitude: float, longitude: float, prediction_date: str | date,
                  field_timezone: str = "Europe/Sofia", *, cache_dir: Path | None = None,
                  profile: str = PROFILE) -> dict:
    model = {PROFILE: "ecmwf_ifs", "era5-soil-0-7-v1": "era5"}.get(profile)
    if not model:
        raise ValueError("UNSUPPORTED_PROFILE")
    params = request_params(latitude, longitude, prediction_date, model)
    cache_dir = cache_dir or ML_ROOT / "data/raw/open_meteo"
    path = cache_dir / f"{window_id(params, prediction_date, field_timezone)}.json"
    if path.exists():
        cached = read_json(path)
        if cached["request"] != params:
            raise ValueError("CACHE_REQUEST_MISMATCH")
    else:
        raise FileNotFoundError(f"Missing static weather snapshot: {path.name}; no download is attempted")
    return normalize_response(cached["response"], latitude=latitude, longitude=longitude,
                              prediction_date=prediction_date, field_timezone=field_timezone,
                              params=params, retrieved_at=cached["retrieved_at"], profile=profile)

