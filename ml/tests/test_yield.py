import sys
from datetime import date
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import yield_dataset  # noqa: E402
import yield_predict  # noqa: E402

TRAINED = (yield_predict.MODEL_DIR / "model.joblib").exists()
MAIZE_ILLINOIS = dict(crop="MAIZE", latitude=40.0, longitude=-89.0, temperature=22.0,
                      soil=0.28, vpd=1.0, sunshine_seconds=9 * 3600)


def test_season_follows_hemisphere():
    assert yield_dataset.season(45, 2020) == (date(2020, 4, 1), date(2020, 9, 30))
    assert yield_dataset.season(10, 2020) == (date(2020, 5, 1), date(2020, 10, 31))
    assert yield_dataset.season(-10, 2020) == (date(2019, 11, 1), date(2020, 4, 30))
    assert yield_dataset.season(-35, 2020) == (date(2019, 10, 1), date(2020, 3, 31))


def test_vpd_is_zero_when_saturated_and_grows_with_heat():
    assert yield_dataset.vpd_kpa(20.0, 100.0) == pytest.approx(0.0)
    assert yield_dataset.vpd_kpa(20.0, 50.0) == pytest.approx(1.169, abs=0.01)
    assert yield_dataset.vpd_kpa(30.0, 50.0) > yield_dataset.vpd_kpa(20.0, 50.0)


def test_distance_is_symmetric_and_zero_for_same_point():
    assert yield_predict.distance_km(40, -89, 40, -89) == pytest.approx(0)
    assert yield_predict.distance_km(0, 0, 0, 1) == pytest.approx(111.2, abs=0.5)


@pytest.mark.skipif(not TRAINED, reason="run yield_train.py first")
def test_typical_maize_weather_gives_estimate_near_normal_for_nearest_region():
    result = yield_predict.predict(MAIZE_ILLINOIS)
    assert result["status"] == "ML_ESTIMATE"
    assert "United States" in result["region"]
    assert 0.5 < result["ratio"] < 1.5
    assert result["expected_yield"] == pytest.approx(result["ratio"] * result["normal_yield"], abs=0.01)


@pytest.mark.skipif(not TRAINED, reason="run yield_train.py first")
def test_winter_weather_is_out_of_range():
    result = yield_predict.predict({**MAIZE_ILLINOIS, "temperature": -15.0})
    assert result["status"] == "OUT_OF_RANGE"


@pytest.mark.skipif(not TRAINED, reason="run yield_train.py first")
def test_unknown_crop_and_bad_input():
    assert yield_predict.predict({**MAIZE_ILLINOIS, "crop": "SPELT"})["status"] == "UNSUPPORTED"
    assert yield_predict.predict({**MAIZE_ILLINOIS, "soil": "wet"})["status"] == "INVALID_INPUT"
