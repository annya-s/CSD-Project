from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

from csd_weather.features import DAILY_UNITS, HOURLY_UNITS, build_features, local_midnight
from csd_weather.weather import normalize_response, request_params


def utc_provider_fixture(target):
    params = request_params(43.5, 24.1, target)
    start = date.fromisoformat(params["start_date"])
    finish = date.fromisoformat(params["end_date"]) + timedelta(days=1)
    stamps = list(range(local_midnight(start, ZoneInfo("UTC")), local_midnight(finish, ZoneInfo("UTC")), 3600))
    local = [datetime.fromtimestamp(stamp, timezone.utc).astimezone(ZoneInfo("Europe/Sofia")) for stamp in stamps]
    count = len(stamps)
    return params, {"latitude": 43.5, "longitude": 24.1, "hourly_units": dict(HOURLY_UNITS),
                    "daily_units": dict(DAILY_UNITS), "hourly": {
                        "time": stamps, "temperature_2m": [12.0] * count,
                        "relative_humidity_2m": [50.0] * count, "vapour_pressure_deficit": [0.7] * count,
                        "soil_moisture_0_to_7cm": [0.3] * count,
                        "sunshine_duration": [3600.0 if 10 <= stamp.hour < 16 else 0.0 for stamp in local]},
                    "daily": {"time": stamps[::24], "sunshine_duration": [123.0] * 16,
                              "daylight_duration": [43200.0] * 16}}


@pytest.mark.parametrize("target,hours", [("2017-04-02", 335), ("2017-11-05", 337)])
def test_provider_utc_padding_is_cut_to_complete_iana_days(target, hours):
    params, raw = utc_provider_fixture(target)
    normalized = normalize_response(raw, latitude=43.5, longitude=24.1, prediction_date=target,
                                    field_timezone="Europe/Sofia", params=params, retrieved_at="2026-01-01T00:00:00Z")
    stamps = normalized["response"]["hourly"]["time"]
    assert len(stamps) == hours
    assert max(stamps) < local_midnight(date.fromisoformat(target), ZoneInfo("Europe/Sofia"))
    assert normalized["response"]["daily"]["sunshine_duration"] == [21600.0] * 14
    assert build_features(normalized, target, "2016-10-01")["sunshine_hours_mean_14d"] == 6
    assert raw["daily"]["sunshine_duration"] == [123.0] * 16


def test_query_requests_explicit_model_depth_and_utc():
    params = request_params(43.5, 24.1, "2017-04-02")
    assert params["models"] == "ecmwf_ifs"
    assert params["timezone"] == "UTC"
    assert "soil_moisture_0_to_7cm" in params["hourly"]
    assert params["start_date"] == "2017-03-18"
    assert params["end_date"] == "2017-04-02"
