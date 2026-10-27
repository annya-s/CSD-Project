from fastapi.testclient import TestClient
import pytest

from csd_weather.service import create_app
from serving_cases import artifacts, request_payload


def test_private_api_requires_token_and_saves_locally(tmp_path, monkeypatch):
    def no_network(*args, **kwargs):
        raise AssertionError("Inference must not contact external services")
    monkeypatch.setattr("requests.sessions.Session.request", no_network)
    app = create_app(artifacts(tmp_path / "artifacts"), tmp_path / "predictions", api_token="private-test-token")
    client = TestClient(app)
    assert client.get("/ready").status_code == 401
    assert client.post("/predict", json=request_payload()).status_code == 401
    headers = {"Authorization": "Bearer private-test-token"}
    assert client.get("/ready", headers=headers).status_code == 200
    response = client.post("/predict", json=request_payload(), headers=headers)
    assert response.status_code == 200 and response.json()["status"] == "EXPERIMENTAL"
    assert len(list((tmp_path / "predictions").glob("*.json"))) == 1
    assert client.get("/docs").status_code == 404


def test_tokenless_api_accepts_only_loopback_and_fails_readiness_without_artifacts(tmp_path):
    app = create_app(tmp_path / "missing", tmp_path / "predictions", api_token="")
    assert TestClient(app, client=("203.0.113.1", 12345)).get("/ready").status_code == 403
    local = TestClient(app, client=("127.0.0.1", 12345))
    assert local.get("/ready").status_code == 503
    assert local.post("/predict", json=request_payload()).json()["status"] == "UNAVAILABLE"


def test_unknown_payload_fields_and_missing_context_are_rejected(tmp_path):
    app = create_app(tmp_path / "missing", tmp_path / "predictions", api_token="test")
    payload = request_payload() | {"upload": True}
    client = TestClient(app)
    assert client.post("/predict", json=payload, headers={"Authorization": "Bearer test"}).status_code == 422


@pytest.mark.parametrize("change", ["response", "hourly", "timezone", "nested_values", "overflow"])
def test_malformed_nested_history_returns_structured_state(tmp_path, change):
    app = create_app(artifacts(tmp_path / "artifacts"), tmp_path / "predictions", api_token="test")
    payload = request_payload()
    if change == "response":
        payload["history"]["response"] = []
    elif change == "hourly":
        payload["history"]["response"]["hourly"] = None
    elif change == "timezone":
        payload["history"]["timezone"] = {}
    elif change == "nested_values":
        payload["history"]["response"]["hourly"]["temperature_2m"] = [[15.0]] * 336
    else:
        payload["history"]["latitude"] = 10 ** 400
    result = TestClient(app).post("/predict", json=payload, headers={"Authorization": "Bearer test"})
    assert result.status_code == 200 and result.json()["status"] == "INSUFFICIENT_HISTORY"
