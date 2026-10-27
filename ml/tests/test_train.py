import json

import joblib
import numpy as np
import pytest
from catboost import CatBoostRegressor

from csd_weather.train import columns_for, predict_candidate, select_candidate, train_models, validate_features
from csd_weather.evaluate import regression_metrics, evaluate_predictions
from training_cases import observed_fixture


def test_selection_uses_validation_and_prefers_simple_tie():
    assert select_candidate({"catboost": {"mae_g_m2": 3}, "median": {"mae_g_m2": 3},
                             "age_only": {"mae_g_m2": 4}}) == "median"


def test_training_rejects_mixed_profiles_nonfinite_and_synthetic():
    for column, value, message in [("feature_profile", "era5-soil-0-7-v1", "one recognized"),
                                   ("temperature_mean_7d", np.inf, "finite"),
                                   ("data_origin", "SYNTHETIC_SCENARIO", "OBSERVED")]:
        frame = observed_fixture()
        frame.loc[0, column] = value
        with pytest.raises(ValueError, match=message):
            validate_features(frame)


@pytest.mark.parametrize("column,value", [("days_since_sowing", 0), ("days_since_sowing", 1.5),
                                         ("latitude", 91), ("longitude", -181),
                                         ("soil_moisture_mean_7d", 25), ("vpd_mean_14d", -0.1),
                                         ("sunshine_hours_mean_14d", 25), ("temperature_mean_7d", -274)])
def test_training_rejects_physical_unit_errors(column, value):
    frame = observed_fixture().astype({column: float})
    frame.loc[0, column] = value
    with pytest.raises(ValueError):
        validate_features(frame)


def test_evaluation_rejects_2d_or_nonfinite_vectors():
    for actual, predicted in [([[1], [2]], [[1], [2]]), ([1], [np.nan])]:
        with pytest.raises(ValueError):
            regression_metrics(actual, predicted)


def test_weather_independence_counts_provider_grids_not_request_ids():
    frame = observed_fixture()
    frame["weather_window_id"] = [f"request-{i}" for i in range(len(frame))]
    frame["weather_grid_latitude"] = 43.5
    frame["weather_grid_longitude"] = 24.1
    result = evaluate_predictions(frame, frame.target_dry_biomass_g_m2)
    assert result["weather_request_windows"] == len(frame)
    assert result["weather_grid_date_groups"] == frame.target_date.nunique()


def test_saved_artifact_matches_and_test_labels_do_not_select(tmp_path):
    frame = observed_fixture()
    result = train_models(frame, tmp_path / "split.json", tmp_path / "artifacts", tmp_path / "reports", iterations=10)
    metadata = json.loads((tmp_path / "artifacts/selected_model.json").read_text())
    if metadata["model_format"] == "catboost":
        model = CatBoostRegressor()
        model.load_model(str(tmp_path / "artifacts" / metadata["model_file"]))
    else:
        model = joblib.load(tmp_path / "artifacts" / metadata["model_file"])
    predictions = predict_candidate(model, metadata["selected_model"], frame)
    assert np.isfinite(predictions).all()
    assert (predictions >= 0).all()
    assert metadata["input_features"] == columns_for(metadata["selected_model"])
    card = json.loads((tmp_path / "artifacts/model_card.json").read_text())
    assert card["confidence"] is None and card["interval"] is None
    assert card["age_support"]["test_outside_age_range"] > 0
    changed = frame.copy()
    changed.loc[changed.season.eq("S17-18"), "target_dry_biomass_g_m2"] *= 100
    changed_result = train_models(changed, tmp_path / "split.json", tmp_path / "artifacts2", tmp_path / "reports2", iterations=10)
    assert changed_result["selected_model"] == result["selected_model"]
    for name in result["models"]:
        assert changed_result["models"][name]["validation"] == result["models"][name]["validation"]
