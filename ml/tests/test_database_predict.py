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
    with pytest.raises(SystemExit) as exc:
        database_predict.main()
    assert exc.value.code == 1
    assert json.loads(capsys.readouterr().out)["status"] == "INVALID_INPUT"


def test_live_requires_identity(monkeypatch):
    monkeypatch.setattr(sys,"argv",["database_predict","--from-database"])
    with pytest.raises(SystemExit): database_predict.main()


@pytest.mark.parametrize("content", ["{broken", "{}", "[]", None])
def test_invalid_fixture_has_same_saved_and_summary_message(tmp_path, monkeypatch, capsys, content):
    from csd_weather.common import INVALID_INPUT_MESSAGE
    path = tmp_path / "fixture.json"
    if content is not None:
        path.write_text(content)
    output = tmp_path / "results"
    monkeypatch.setattr(sys, "argv", ["database_predict", "--fixture", str(path), "--output-dir", str(output)])
    with pytest.raises(SystemExit) as exc:
        database_predict.main()
    assert exc.value.code == 1
    summary = json.loads(capsys.readouterr().out)
    saved = json.loads(next(output.glob("*.json")).read_text())
    assert summary["status"] == saved["status"] == "INVALID_INPUT"
    assert summary["message"] == saved["message"] == INVALID_INPUT_MESSAGE
    assert saved["support_reasons"] == ["INVALID_INPUT"]


def test_disabled_database_remains_unavailable(tmp_path, monkeypatch, capsys):
    monkeypatch.setenv("SUPABASE_READ_ENABLED", "false")
    monkeypatch.setattr(sys, "argv", ["database_predict", "--from-database", "--farmer-id", "1",
        "--planted-at", "2017-01-01T00:00:00Z", "--prediction-date", "2017-04-24", "--output-dir", str(tmp_path)])
    with pytest.raises(SystemExit) as exc:
        database_predict.main()
    assert exc.value.code == 1
    summary = json.loads(capsys.readouterr().out)
    assert summary["status"] == "UNAVAILABLE"
    assert "message" not in summary


@pytest.mark.parametrize("failure", [RuntimeError("private details"), OSError("private details")])
def test_local_model_failure_is_unavailable(tmp_path, monkeypatch, capsys, failure):
    def broken(*args):
        raise failure
    monkeypatch.setattr(database_predict, "Predictor", broken)
    monkeypatch.setattr(sys, "argv", ["database_predict", "--output-dir", str(tmp_path)])
    with pytest.raises(SystemExit):
        database_predict.main()
    captured = capsys.readouterr()
    assert json.loads(captured.out)["status"] == "UNAVAILABLE"
    assert "private details" not in captured.out
