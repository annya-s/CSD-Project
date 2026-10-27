"""Constructed weather fixtures validate the contract, not crop behaviour."""
import copy
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

from csd_weather.common import FEATURE_NAMES, PROFILE
from csd_weather.features import DAILY_UNITS, HOURLY_UNITS, build_features, local_midnight


def history_fixture(target="2017-04-02", zone_name="Europe/Sofia"):
    target = date.fromisoformat(target)
    start, zone = target - timedelta(days=14), ZoneInfo(zone_name)
    stamps = list(range(local_midnight(start, zone), local_midnight(target, zone), 3600))
    local = [datetime.fromtimestamp(stamp, timezone.utc).astimezone(zone) for stamp in stamps]
    hourly = {"time": stamps,
              "temperature_2m": [10.0 if item.date() < target - timedelta(days=7) else 20.0 for item in local],
              "relative_humidity_2m": [60.0] * len(stamps),
              "vapour_pressure_deficit": [0.8] * len(stamps),
              "soil_moisture_0_to_7cm": [0.3] * len(stamps),
              "sunshine_duration": [3600.0 if 10 <= item.hour < 16 else 0.0 for item in local]}
    daily = {"time": [local_midnight(start + timedelta(days=i), zone) for i in range(14)],
             "sunshine_duration": [21600.0] * 14, "daylight_duration": [43200.0] * 14}
    return {"schema_version": "weather-history-v1", "profile": PROFILE, "timezone": zone_name,
            "response": {"hourly_units": dict(HOURLY_UNITS), "daily_units": dict(DAILY_UNITS),
                         "hourly": hourly, "daily": daily}}


@pytest.mark.parametrize("target,hours", [("2017-04-02", 335), ("2017-11-05", 337), ("2017-06-20", 336)])
def test_exact_local_days_dst_and_seconds_to_hours(target, hours):
    history = history_fixture(target)
    result = build_features(history, target, "2016-10-01")
    assert len(history["response"]["hourly"]["time"]) == hours
    assert list(result) == FEATURE_NAMES
    assert result["temperature_mean_7d"] == pytest.approx(20)
    assert result["temperature_mean_14d"] == pytest.approx(15)
    assert result["sunshine_hours_mean_14d"] == pytest.approx(6)


@pytest.mark.parametrize("mutation,reason", [
    ("missing_hour", "INCOMPLETE_OR_MISALIGNED_HOURS"),
    ("future_hour", "INCOMPLETE_OR_MISALIGNED_HOURS"),
    ("duplicate_hour", "INCOMPLETE_OR_MISALIGNED_HOURS"),
    ("missing_day", "INCOMPLETE_OR_MISALIGNED_DAYS"),
    ("null", "MISSING_HOURLY_VALUES"),
    ("nonfinite", "NONFINITE_HOURLY_VALUES"),
    ("depth", "INCOMPATIBLE_UNITS_OR_DEPTH"),
    ("unit", "INCOMPATIBLE_UNITS_OR_DEPTH"),
    ("negative_vpd", "INVALID_VPD"),
    ("sunshine", "INCONSISTENT_SUNSHINE"),
])
def test_rejects_bad_or_future_weather(mutation, reason):
    history = history_fixture()
    response = history["response"]
    hourly = response["hourly"]
    if mutation == "missing_hour":
        hourly["time"].pop()
    elif mutation == "future_hour":
        hourly["time"].append(hourly["time"][-1] + 3600)
    elif mutation == "duplicate_hour":
        hourly["time"][1] = hourly["time"][0]
    elif mutation == "missing_day":
        response["daily"]["time"].pop()
    elif mutation == "null":
        hourly["temperature_2m"][0] = None
    elif mutation == "nonfinite":
        hourly["temperature_2m"][0] = float("nan")
    elif mutation == "depth":
        response["hourly_units"]["soil_moisture_3_to_9cm"] = response["hourly_units"].pop("soil_moisture_0_to_7cm")
    elif mutation == "unit":
        response["daily_units"]["sunshine_duration"] = "h"
    elif mutation == "negative_vpd":
        hourly["vapour_pressure_deficit"][0] = -1
    elif mutation == "sunshine":
        response["daily"]["sunshine_duration"][0] -= 3600
    with pytest.raises(ValueError, match=reason):
        build_features(history, "2017-04-02", "2016-10-01")


def test_builder_does_not_modify_input():
    history = history_fixture()
    before = copy.deepcopy(history)
    build_features(history, "2017-04-02", "2016-10-01")
    assert history == before
