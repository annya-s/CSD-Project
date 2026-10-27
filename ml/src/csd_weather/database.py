"""Read the proposed ML history contract; no weather fetching or database writes."""
from __future__ import annotations

import os
from datetime import date, datetime, timedelta, timezone
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import requests

from .common import PROFILE
from .contracts import PredictionRequest
from .features import DAILY_UNITS, HOURLY_UNITS, build_features, local_midnight

CONTEXT_COLUMNS = "farmer_id,crop_type,planted_at,profile,variant,growing_system,sowing_date,latitude,longitude,timezone,soil_depth_cm"
SCOPE_COLUMNS = "farmer_id,crop_type,planted_at,profile"
HOURLY_COLUMNS = SCOPE_COLUMNS + ",observed_at,temperature_2m,relative_humidity_2m,vapour_pressure_deficit,soil_moisture_0_to_7cm,sunshine_duration"
DAILY_COLUMNS = SCOPE_COLUMNS + ",local_date,daylight_duration"


class DatabaseInputError(ValueError):
    def __init__(self, reason: str, status: str = "INSUFFICIENT_HISTORY"):
        super().__init__(reason)
        self.reason, self.status = reason, status


def instant(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    if parsed.utcoffset() is None:
        raise ValueError("Timezone required")
    return parsed.astimezone(timezone.utc)


def scope(farmer_id: int, planted_at: str, profile: str = PROFILE) -> dict:
    if isinstance(farmer_id, bool) or not isinstance(farmer_id, int) or not 0 < farmer_id <= 9223372036854775807:
        raise DatabaseInputError("INVALID_OWNER", "UNSUPPORTED")
    if profile != PROFILE:
        raise DatabaseInputError("UNSUPPORTED_PROFILE", "UNSUPPORTED")
    try:
        planted = instant(planted_at).isoformat()
    except (ValueError, TypeError, OverflowError):
        raise DatabaseInputError("INVALID_PLANTING_TIMESTAMP", "UNSUPPORTED") from None
    return dict(farmer_id=farmer_id, crop_type="WHEAT", planted_at=planted, profile=profile)


def same_scope(row: dict, expected: dict) -> bool:
    return (isinstance(row, dict) and type(row.get("farmer_id")) is int and row["farmer_id"] == expected["farmer_id"]
            and row.get("crop_type") == expected["crop_type"] and row.get("profile") == expected["profile"]
            and instant(row["planted_at"]).isoformat() == expected["planted_at"])


def validate_context(context: dict, expected: dict, target: date) -> ZoneInfo:
    try:
        if not same_scope(context, expected):
            raise ValueError()
        if context["variant"] != "winter_wheat" or context["growing_system"] != "outdoor_soil" or context["soil_depth_cm"] != "0-7":
            raise ValueError()
        zone = ZoneInfo(context["timezone"])
        sowing = date.fromisoformat(context["sowing_date"])
        if target < date(1, 1, 15) or sowing >= target or target > datetime.now(zone).date() or instant(context["planted_at"]).astimezone(zone).date() > target:
            raise ValueError()
        return zone
    except (ValueError, KeyError, TypeError, AttributeError, OverflowError):
        raise DatabaseInputError("INVALID_OR_UNSUPPORTED_CONTEXT", "UNSUPPORTED") from None


def request_from_rows(bundle: dict, *, farmer_id: int, planted_at: str, prediction_date: str) -> PredictionRequest:
    """Caller supplies a trusted, backend-authorized planting identity, never browser ownership."""
    expected = scope(farmer_id, planted_at)
    try:
        target = date.fromisoformat(prediction_date)
        context = bundle["context"]
        zone = validate_context(context, expected, target)
        start = target - timedelta(days=14)
        hours, days = bundle["hourly"], bundle["daily"]
        if not isinstance(hours, list) or not isinstance(days, list) or len(hours) > 360 or len(days) != 14:
            raise ValueError()
        if any(not same_scope(row, expected) for row in hours + days):
            raise ValueError()
        if any(type(row[key]) not in {int, float} for row in hours for key in HOURLY_UNITS if key != "time") or any(type(row["daylight_duration"]) not in {int, float} for row in days):
            raise ValueError()
        hours = sorted(hours, key=lambda row: instant(row["observed_at"]))
        days = sorted(days, key=lambda row: date.fromisoformat(row["local_date"]))
        times = [instant(row["observed_at"]).timestamp() for row in hours]
        expected_times = list(range(local_midnight(start, zone), local_midnight(target, zone), 3600))
        if times != expected_times or [row["local_date"] for row in days] != [(start + timedelta(days=i)).isoformat() for i in range(14)]:
            raise ValueError()
        # Units are fixed by the proposed SQL column contract; never infer VPD/RH/depth.
        hourly = {key: [row[key] for row in hours] for key in HOURLY_UNITS if key != "time"}
        hourly["time"] = [int(t) for t in times]
        daily = {"time": [local_midnight(date.fromisoformat(row["local_date"]), zone) for row in days],
                 "daylight_duration": [row["daylight_duration"] for row in days],
                 "sunshine_duration": [sum(row["sunshine_duration"] for row in hours
                    if instant(row["observed_at"]).astimezone(zone).date().isoformat() == day["local_date"]) for day in days]}
        history = dict(schema_version="weather-history-v1", profile=expected["profile"], timezone=context["timezone"],
                       latitude=context["latitude"], longitude=context["longitude"],
                       response=dict(hourly_units=HOURLY_UNITS.copy(), daily_units=DAILY_UNITS.copy(), hourly=hourly, daily=daily))
        build_features(history, target, context["sowing_date"], expected["profile"])
        return PredictionRequest(crop=context["variant"], growing_system=context["growing_system"],
            sowing_date=context["sowing_date"], prediction_date=target, latitude=context["latitude"],
            longitude=context["longitude"], history=history, farmer_id=farmer_id, planted_at=expected["planted_at"])
    except DatabaseInputError:
        raise
    except (ValueError, KeyError, TypeError, OverflowError, AttributeError):
        raise DatabaseInputError("INVALID_OR_INCOMPLETE_STORED_HISTORY") from None


class SupabaseHistoryReader:
    """Explicitly enabled server-only Data API reader, using bounded complete responses."""
    def __init__(self, *, environment=None, session=None):
        env = os.environ if environment is None else environment
        if env.get("SUPABASE_READ_ENABLED", "false").lower() != "true":
            raise RuntimeError("Supabase reads are disabled; set SUPABASE_READ_ENABLED=true after setup")
        self.url = env.get("SUPABASE_URL", "").rstrip("/")
        parsed = urlparse(self.url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in {"", "/"}:
            raise ValueError("SUPABASE_URL must be an HTTPS project origin")
        secret, legacy = env.get("SUPABASE_SECRET_KEY", ""), env.get("SUPABASE_SERVICE_ROLE_KEY", "")
        self.headers = {"Accept": "application/json", "Prefer": "count=exact"}
        if secret.startswith("sb_secret_"):
            self.headers["apikey"] = secret
        elif not secret and legacy.startswith("eyJ"):
            self.headers.update(apikey=legacy, Authorization=f"Bearer {legacy}")
        else:
            raise ValueError("Configure a server-only Supabase secret key")
        self.session = session or requests.Session()

    def _get(self, table: str, columns: str, filters: list, cap: int, order: str) -> list:
        try:
            response = self.session.get(self.url + "/rest/v1/" + table,
                params=[("select", columns), ("order", order), ("limit", str(cap))] + filters,
                headers=self.headers, timeout=(5, 30), allow_redirects=False)
            if response.status_code not in {200, 206}:
                raise RuntimeError(f"Supabase read failed (HTTP {response.status_code})")
            rows = response.json()
            # Require exact count so a project row limit cannot silently truncate history.
            count = response.headers.get("Content-Range", "").split("/")[-1]
            if not isinstance(rows, list) or not count.isdigit() or int(count) != len(rows) or len(rows) >= cap:
                raise DatabaseInputError("INCOMPLETE_DATABASE_RESPONSE")
            return rows
        except requests.RequestException:
            raise RuntimeError("Supabase read failed due to a network error") from None
        except ValueError as exc:
            if isinstance(exc, DatabaseInputError):
                raise
            raise DatabaseInputError("INVALID_DATABASE_RESPONSE") from None

    def read(self, *, farmer_id: int, planted_at: str, prediction_date: str) -> dict:
        expected = scope(farmer_id, planted_at)
        try:
            target = date.fromisoformat(prediction_date)
        except (ValueError, TypeError):
            raise DatabaseInputError("INVALID_PREDICTION_DATE", "UNSUPPORTED") from None
        filters = [(key, "eq." + str(value)) for key, value in expected.items()]
        contexts = self._get("ml_planting_context", CONTEXT_COLUMNS, filters, 2, "planted_at.asc")
        if len(contexts) != 1:
            raise DatabaseInputError("MISSING_OR_AMBIGUOUS_CONTEXT", "UNSUPPORTED")
        context = contexts[0]
        zone = validate_context(context, expected, target)
        start = target - timedelta(days=14)
        utc = lambda day: datetime.fromtimestamp(local_midnight(day, zone), timezone.utc).isoformat()
        hours = self._get("ml_weather_hourly", HOURLY_COLUMNS, filters + [("observed_at", "gte." + utc(start)),
            ("observed_at", "lt." + utc(target))], 361, "observed_at.asc")
        days = self._get("ml_weather_daily", DAILY_COLUMNS, filters + [("local_date", "gte." + start.isoformat()),
            ("local_date", "lt." + target.isoformat())], 15, "local_date.asc")
        return dict(context=context, hourly=hours, daily=days)
