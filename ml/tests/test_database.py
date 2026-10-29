import copy
import json
from datetime import date, datetime, timedelta, timezone
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import pytest
import requests

from csd_weather.common import ML_ROOT
from csd_weather.contracts import PredictionRequest
from csd_weather.database import DatabaseInputError, SupabaseHistoryReader, request_from_rows
from csd_weather.features import build_features, local_midnight
from csd_weather.predict import Predictor


def fixture():
    return json.loads((ML_ROOT / "examples/database_fixture.json").read_text(encoding="utf-8"))


def identity(bundle):
    return dict(farmer_id=bundle["context"]["farmer_id"], planted_at=bundle["context"]["planted_at"], prediction_date=bundle["prediction_date"])


def test_fixture_matches_golden_features_and_prediction():
    bundle = fixture()
    request = request_from_rows(bundle, **identity(bundle))
    golden = PredictionRequest.model_validate_json((ML_ROOT / "examples/request.json").read_text(encoding="utf-8"))
    model = Predictor()
    actual, expected = model.predict(request), model.predict(golden)
    assert actual["features"] == expected["features"]
    assert actual["value"] == expected["value"] == pytest.approx(152.0214, abs=0.0001)


@pytest.mark.parametrize("change", [
    lambda b: b["hourly"].pop(), lambda b: b["hourly"].append(b["hourly"][0]),
    lambda b: b["daily"].pop(), lambda b: b["daily"].__setitem__(0,b["daily"][1]),
    lambda b: b["hourly"][0].update(farmer_id=1002),
    lambda b: b["hourly"][0].update(profile="era5-soil-0-7-v1"),
    lambda b: b["hourly"][0].update(vapour_pressure_deficit=None),
    lambda b: b["hourly"][0].update(relative_humidity_2m=True),
    lambda b: b["hourly"][0].update(observed_at="2099-01-01T00:00:00Z"),
    lambda b: b["hourly"][0].update(observed_at="2017-04-09T21:00:00.5Z"),
    lambda b: b["context"].update(soil_depth_cm="3-9"),
    lambda b: b["context"].update(timezone="Invalid/Zone"),
    lambda b: b["context"].update(variant="spring_wheat"),
    lambda b: b["context"].update(profile="other"),
    lambda b: b.update(context=None), lambda b: b["hourly"].__setitem__(0,None),
    lambda b: b.update(context=[]), lambda b: b["daily"].__setitem__(0,"bad"),
])
def test_bad_rows_fail_closed(change):
    bundle = fixture(); args = identity(bundle); change(bundle)
    with pytest.raises(DatabaseInputError):
        request_from_rows(bundle, **args)


def test_disabled_read_never_constructs_transport(monkeypatch):
    monkeypatch.setattr(requests, "Session", lambda: pytest.fail("network client created"))
    with pytest.raises(RuntimeError, match="disabled"):
        SupabaseHistoryReader(environment={})


ENV = dict(SUPABASE_READ_ENABLED="true", SUPABASE_URL="https://example.supabase.co", SUPABASE_SECRET_KEY="sb_secret_test")


class Transport:
    def __init__(self, bundle, count=None):
        self.responses = [[bundle["context"]], bundle["hourly"], bundle["daily"]]
        self.calls = []; self.count = count
    def get(self, url, **kwargs):
        self.calls.append((url, kwargs)); rows = self.responses.pop(0)
        return SimpleNamespace(status_code=200, json=lambda: rows,
            headers={"Content-Range": f"0-{len(rows)-1}/{self.count if self.count is not None else len(rows)}"})


def test_live_reader_uses_fixed_queries_and_modern_key():
    bundle=fixture(); transport=Transport(bundle)
    rows=SupabaseHistoryReader(environment=ENV, session=transport).read(**identity(bundle))
    assert request_from_rows(rows, **identity(bundle))
    for url, call in transport.calls:
        assert url.startswith(ENV["SUPABASE_URL"] + "/rest/v1/ml_")
        assert call["allow_redirects"] is False and call["timeout"] == (5,30)
        assert "Authorization" not in call["headers"]
        assert dict(call["params"])["select"] != "*"
    filters = transport.calls[1][1]["params"]
    assert ("observed_at", "gte.2017-04-09T21:00:00+00:00") in filters
    assert ("observed_at", "lt.2017-04-23T21:00:00+00:00") in filters


@pytest.mark.parametrize("count", ["*", "2", "missing"])
def test_truncated_or_unverifiable_response_rejected(count):
    bundle=fixture()
    with pytest.raises(DatabaseInputError):
        SupabaseHistoryReader(environment=ENV, session=Transport(bundle,count)).read(**identity(bundle))


@pytest.mark.parametrize("target,expected_hours", [("2017-03-30",335), ("2017-11-02",337)])
def test_dst_local_days_and_utc_bounds(target, expected_hours):
    bundle=fixture(); zone=ZoneInfo(bundle["context"]["timezone"]); day=date.fromisoformat(target); start=day-timedelta(days=14)
    bundle["prediction_date"]=target
    times=range(local_midnight(start,zone),local_midnight(day,zone),3600)
    template=bundle["hourly"][0]
    bundle["hourly"]=[dict(template, observed_at=datetime.fromtimestamp(t,timezone.utc).isoformat(),sunshine_duration=0) for t in times]
    bundle["daily"]=[dict(bundle["daily"][0],local_date=(start+timedelta(days=i)).isoformat()) for i in range(14)]
    assert len(bundle["hourly"]) == expected_hours
    transport=Transport(bundle)
    loaded=SupabaseHistoryReader(environment=ENV,session=transport).read(**identity(bundle))
    request=request_from_rows(loaded,**identity(bundle))
    assert build_features(request.history,target,request.sowing_date)
    bounds=[v for k,v in transport.calls[1][1]["params"] if k=="observed_at"]
    assert (datetime.fromisoformat(bounds[1][3:])-datetime.fromisoformat(bounds[0][4:])).total_seconds()==expected_hours*3600


def test_legacy_key_and_sanitized_network_error():
    class Broken:
        def get(self,*args,**kwargs):
            assert kwargs["headers"]["Authorization"] == "Bearer eyJtest"
            raise requests.ConnectionError("SECRET")
    env={**ENV,"SUPABASE_SECRET_KEY":"","SUPABASE_SERVICE_ROLE_KEY":"eyJtest"}
    with pytest.raises(RuntimeError, match="network error") as exc:
        SupabaseHistoryReader(environment=env,session=Broken()).read(**identity(fixture()))
    assert "SECRET" not in str(exc.value)


def test_saved_fixture_cannot_use_generic_export(monkeypatch):
    from csd_weather.persistence import prediction_row, upload_prediction
    bundle=fixture()
    prediction=Predictor().predict(request_from_rows(bundle,**identity(bundle)))
    prediction["input_source"]="local_database_fixture"
    with pytest.raises(ValueError,match="Fixture"):
        prediction_row(prediction)
    monkeypatch.setattr(requests,"Session",lambda: pytest.fail("Fixture created network client"))
    with pytest.raises(ValueError,match="Fixture"):
        upload_prediction(prediction,environment={**ENV,"SUPABASE_ENABLED":"true"})


@pytest.mark.parametrize("timestamp", ["0001-01-01T00:00:00+01:00", "9999-12-31T23:59:59-01:00"])
def test_extreme_offset_timestamp_is_structured_error(timestamp):
    from csd_weather.database import scope
    with pytest.raises(DatabaseInputError) as exc:
        scope(1, timestamp)
    assert exc.value.status == "INVALID_INPUT"
    assert exc.value.reason == "INVALID_INPUT"



def test_unsupported_growing_system_stays_distinct():
    bundle = fixture()
    bundle["context"]["growing_system"] = "hydroponic"
    with pytest.raises(DatabaseInputError) as exc:
        request_from_rows(bundle, **identity(bundle))
    assert exc.value.status == "UNSUPPORTED"
    assert exc.value.reason == "UNSUPPORTED_CONTEXT"
