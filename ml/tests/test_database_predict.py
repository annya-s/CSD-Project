import json
import sys

import pytest

from csd_weather import database_predict


def test_default_cli_offline_saves_prediction(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr("requests.Session", lambda: pytest.fail("Fixture contacted network"))
    monkeypatch.setattr(sys,"argv",["database_predict","--output-dir",str(tmp_path)])
    database_predict.main()
    summary=json.loads(capsys.readouterr().out)
    assert summary["value"] == pytest.approx(152.0214,abs=0.0001)
    saved=json.loads(next(tmp_path.glob("*.json")).read_text())
    assert saved["input_source"] == "local_database_fixture"


def test_fixture_upload_rejected(monkeypatch):
    monkeypatch.setattr(sys,"argv",["database_predict","--upload"])
    with pytest.raises(SystemExit): database_predict.main()


def test_bad_history_saved_as_structured_result(tmp_path, monkeypatch, capsys):
    from test_database import fixture
    bundle=fixture(); bundle["hourly"].pop()
    path=tmp_path/"fixture.json"; path.write_text(json.dumps(bundle))
    out=tmp_path/"results"
    monkeypatch.setattr(sys,"argv",["database_predict","--fixture",str(path),"--output-dir",str(out)])
    database_predict.main()
    assert json.loads(capsys.readouterr().out)["status"] == "INSUFFICIENT_HISTORY"


def test_live_requires_identity(monkeypatch):
    monkeypatch.setattr(sys,"argv",["database_predict","--from-database"])
    with pytest.raises(SystemExit): database_predict.main()
