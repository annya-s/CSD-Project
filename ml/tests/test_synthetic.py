import copy
import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
import pytest

from csd_weather import synthetic
from csd_weather.features import HOURLY_UNITS, DAILY_UNITS, local_midnight
from csd_weather.splits import make_splits
from training_cases import observed_fixture


def fixture_windows(frame):
    # Minimal auxiliary arrays test scenario mechanics. Full hourly coverage is tested
    # in test_features and the real-data end-to-end generation command.
    history = {"profile": "ifs-soil-0-7-v1", "response": {
        "hourly_units": {"soil_moisture_0_to_7cm": "m³/m³"},
        "hourly": {"time": [0, 3600, 7200], "temperature_2m": [15, 16, 17],
                   "relative_humidity_2m": [60, 60, 60], "vapour_pressure_deficit": [0.7, 0.8, 0.9],
                   "soil_moisture_0_to_7cm": [0.3, 0.3, 0.3], "sunshine_duration": [1000, 1000, 1000]},
        "daily": {"time": [0], "sunshine_duration": [3000], "daylight_duration": [40000]}}}
    return {visit: copy.deepcopy(history) for visit in frame.visit_id}


def feature_stub(history, *args, **kwargs):
    hourly = history["response"]["hourly"]
    if len(hourly["time"]) != 3:
        raise ValueError("Incomplete hourly coverage")
    if len(set(hourly["time"])) != len(hourly["time"]):
        raise ValueError("Duplicate hourly timestamp")
    if history["response"]["hourly_units"]["soil_moisture_0_to_7cm"] != "m³/m³":
        raise ValueError("Wrong soil-moisture units")
    return {"temperature_mean_7d": float(np.mean(hourly["temperature_2m"]))}


def scenario_config():
    path = Path(__file__).resolve().parents[1] / "config/synthetic_scenarios_v1.json"
    result = json.loads(path.read_text())
    result["counts"] = {"resampled": 7, "perturbed": 8, "invalid": 4}
    return result


def test_scenarios_are_deterministic_training_only_and_unlabelled(monkeypatch):
    monkeypatch.setattr(synthetic, "build_features", feature_stub)
    frame = observed_fixture()
    splits = make_splits(frame)
    windows = fixture_windows(frame)
    original = copy.deepcopy(windows)
    first, manifest = synthetic.generate_scenarios(frame, splits, windows, scenario_config())
    second, _ = synthetic.generate_scenarios(frame, splits, windows, scenario_config())
    assert first == second
    assert windows == original
    assert len(first) == 19
    assert manifest["total"] == 19
    for scenario in first:
        assert splits["assignments"][scenario["parent_visit_id"]] == "train"
        assert scenario["target_dry_biomass_g_m2"] is None
        assert scenario["data_origin"] == "SYNTHETIC_SCENARIO"
        assert scenario["validation_passed"] == (scenario["scenario_group"] != "invalid")
    warmer = next(item for item in first if item["scenario_family"] == "warmer_at_same_rh")
    hourly = warmer["history"]["response"]["hourly"]
    expected = synthetic.saturation_vapour_pressure(np.asarray(hourly["temperature_2m"])) * 0.4
    np.testing.assert_allclose(hourly["vapour_pressure_deficit"], expected)
    reduced = next(item for item in first if item["scenario_family"] == "reduced_sunshine")
    assert sum(reduced["history"]["response"]["hourly"]["sunshine_duration"]) == pytest.approx(
        reduced["history"]["response"]["daily"]["sunshine_duration"][0])


def test_generator_rejects_no_eligible_training_windows(monkeypatch):
    monkeypatch.setattr(synthetic, "build_features", feature_stub)
    frame = observed_fixture()
    with pytest.raises(ValueError, match="No valid training-only"):
        synthetic.generate_scenarios(frame, make_splits(frame), {}, scenario_config())


def test_scenarios_pass_real_feature_builder_across_daylight_saving():
    frame = observed_fixture()
    # The 14-day window ending 27 March includes the 23-hour spring transition.
    first_date = frame.target_date.eq("2017-03-20")
    frame.loc[first_date, "target_date"] = "2017-03-27"
    frame.loc[first_date, "days_since_sowing"] += 7
    zone = ZoneInfo("Europe/Sofia")
    windows = {}
    for row in frame.to_dict("records"):
        target = date.fromisoformat(row["target_date"])
        days = [target - timedelta(days=14-i) for i in range(14)]
        stamps = list(range(local_midnight(days[0], zone), local_midnight(target, zone), 3600))
        n = len(stamps)
        per_day = [sum(datetime.fromtimestamp(stamp, timezone.utc).astimezone(zone).date() == day
                       for stamp in stamps) for day in days]
        windows[row["visit_id"]] = {
            "schema_version": "weather-history-v1", "profile": row["feature_profile"], "timezone": "Europe/Sofia",
            "response": {"hourly_units": HOURLY_UNITS.copy(), "daily_units": DAILY_UNITS.copy(),
                         "hourly": {"time": stamps, "temperature_2m": [15.0]*n,
                                    "relative_humidity_2m": [60.0]*n, "vapour_pressure_deficit": [0.7]*n,
                                    "soil_moisture_0_to_7cm": [0.3]*n, "sunshine_duration": [1000.0]*n},
                         "daily": {"time": [local_midnight(day, zone) for day in days],
                                   "sunshine_duration": [hours*1000.0 for hours in per_day],
                                   "daylight_duration": [40000.0]*14}}}
    assert any(len(history["response"]["hourly"]["time"]) == 335 for history in windows.values())
    scenarios, manifest = synthetic.generate_scenarios(frame, make_splits(frame), windows, scenario_config())
    assert manifest["eligible_training_seeds"] == 12
    assert manifest["validation_passed"] == 15
    assert manifest["validation_rejected"] == 4
    assert all(item["features"]["days_since_sowing"] > 0 for item in scenarios if item["validation_passed"])
    assert all(item["validation_error"] for item in scenarios if item["scenario_group"] == "invalid")
