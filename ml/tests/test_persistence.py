from types import SimpleNamespace

import pytest
import requests

from csd_weather.persistence import prediction_row, upload_prediction


def prediction():
    return {"id": "1f8c0bf6-1362-4f2a-8f30-7936f2100112", "farmer_id": 12,
            "planted_at": "2022-10-01T00:00:00Z", "schema_version": "wheat-prediction-v1",
            "crop_type": "WHEAT", "prediction_date": "2023-05-15", "model_version": "test-v1",
            "status": "EXPERIMENTAL", "created_at": "2023-05-15T12:00:00Z"}


def test_export_disabled_before_any_network_or_credentials(monkeypatch):
    monkeypatch.setattr("requests.Session", lambda: pytest.fail("Disabled export created a network client"))
    with pytest.raises(RuntimeError, match="disabled"):
        upload_prediction(prediction(), environment={})


@pytest.mark.parametrize("farmer_id", [None, True, -1, "12", 2**63])
def test_export_requires_trusted_application_identity(farmer_id):
    with pytest.raises(ValueError, match="farmer_id"):
        prediction_row(prediction() | {"farmer_id": farmer_id})


def test_modern_secret_uses_apikey_and_idempotent_insert():
    calls = []
    class Session:
        def post(self, url, **kwargs):
            calls.append((url, kwargs))
            return SimpleNamespace(status_code=201)
    env = {"SUPABASE_ENABLED": "true", "SUPABASE_URL": "https://example.supabase.co", "SUPABASE_SECRET_KEY": "sb_secret_fixture"}
    result = upload_prediction(prediction(), environment=env, session=Session())
    assert result["uploaded"]
    url, kwargs = calls[0]
    assert url == "https://example.supabase.co/rest/v1/ml_predictions"
    assert kwargs["headers"]["apikey"] == "sb_secret_fixture"
    assert "Authorization" not in kwargs["headers"]
    assert kwargs["params"] == {"on_conflict": "id"} and kwargs["allow_redirects"] is False
    assert kwargs["json"]["payload"] == prediction()


def test_network_failure_does_not_expose_credentials():
    class Session:
        def post(self, *args, **kwargs):
            raise requests.ConnectionError("secret-string")
    with pytest.raises(RuntimeError) as error:
        upload_prediction(prediction(), environment={"SUPABASE_ENABLED": "true", "SUPABASE_URL": "https://example.supabase.co", "SUPABASE_SECRET_KEY": "sb_secret_fixture"}, session=Session())
    assert "secret-string" not in str(error.value)
