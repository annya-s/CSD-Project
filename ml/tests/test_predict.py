import json

import pytest
from pydantic import ValidationError

from csd_weather.contracts import PredictionRequest
from csd_weather.predict import Predictor
from serving_cases import artifacts, request_payload


def test_prediction_is_local_repeatable_and_experimental(tmp_path, monkeypatch):
    monkeypatch.setattr("requests.sessions.Session.request", lambda *a, **k: pytest.fail("Prediction attempted network"))
    predictor = Predictor(artifacts(tmp_path / "artifacts"))
    request = PredictionRequest.model_validate(request_payload())
    first, second = predictor.predict(request), predictor.predict(request)
    assert first["status"] == "EXPERIMENTAL" and first["value"] == 250
    assert first["id"] == second["id"] and first["input_hash"] == second["input_hash"]
    assert first["confidence"] is None and first["interval"] is None
    assert "WEATHER_IMPROVEMENT_NOT_ESTABLISHED" in first["support_reasons"]


@pytest.mark.parametrize("change", ["checksum", "profile", "version", "schema", "model_name", "bounds", "corrupt_joblib"])
def test_invalid_artifacts_are_unavailable(tmp_path, change):
    directory = artifacts(tmp_path / "artifacts")
    filename = "selected_model.json" if change in {"checksum", "corrupt_joblib"} else "feature_schema.json" if change in {"schema", "bounds"} else "model_card.json"
    path = directory / filename
    value = json.loads(path.read_text())
    if change == "checksum":
        value["artifact_sha256"] = "0" * 64
    elif change == "profile":
        value["feature_profile"] = "wrong"
    elif change == "version":
        value["model_version"] = "wrong"
    elif change == "schema":
        value["feature_names"] = []
    elif change == "model_name":
        value["selected_model"] = "wrong"
    elif change == "bounds":
        value["training_ranges"] = {}
    else:
        import hashlib
        (directory / "median.joblib").write_bytes(b"invalid")
        value["artifact_sha256"] = hashlib.sha256(b"invalid").hexdigest()
    path.write_text(json.dumps(value))
    predictor = Predictor(directory)
    assert not predictor.ready
    assert predictor.predict(PredictionRequest.model_validate(request_payload()))["status"] == "UNAVAILABLE"


@pytest.mark.parametrize("change,expected", [("crop", "UNSUPPORTED"), ("system", "UNSUPPORTED"),
                                            ("region", "UNSUPPORTED"), ("range", "UNSUPPORTED"),
                                            ("history", "INSUFFICIENT_HISTORY")])
def test_support_boundaries(tmp_path, change, expected):
    predictor = Predictor(artifacts(tmp_path / "artifacts"))
    payload = request_payload()
    if change == "crop":
        payload["crop"] = "lettuce"
    elif change == "system":
        payload["growing_system"] = "hydroponic"
    elif change == "region":
        payload["latitude"] = payload["history"]["latitude"] = 1.35
    elif change == "range":
        payload["sowing_date"] = "2020-01-01"
    else:
        payload["history"]["response"]["hourly"]["time"].pop()
    result = predictor.predict(PredictionRequest.model_validate(payload))
    assert result["status"] == expected and result["value"] is None


def test_crop_context_is_required():
    payload = request_payload()
    del payload["crop"]
    with pytest.raises(ValidationError):
        PredictionRequest.model_validate(payload)


def test_nonfinite_nested_input_is_rejected_before_hashing():
    payload = request_payload()
    payload["history"]["response"]["hourly"]["temperature_2m"][0] = float("nan")
    with pytest.raises(ValidationError):
        PredictionRequest.model_validate(payload)
