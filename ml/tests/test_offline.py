import pytest

from csd_weather.prepare import SOURCE_FILES, import_local_sources
from csd_weather.weather import load_cached_history


def test_local_import_preserves_originals_without_metadata_or_network(tmp_path, monkeypatch):
    monkeypatch.setattr("requests.sessions.Session.request", lambda *a, **k: pytest.fail("Offline import attempted network"))
    raw = tmp_path / "data/raw"
    raw.mkdir(parents=True)
    for name in SOURCE_FILES:
        (raw / name).write_text("unchanged original", encoding="utf-8")
    result = import_local_sources(tmp_path)
    assert all((result / name).read_bytes() == (raw / name).read_bytes() for name in SOURCE_FILES)
    assert (tmp_path / "data/manifests/local_sources.json").exists()
    (raw / SOURCE_FILES[0]).write_text("changed original")
    with pytest.raises(ValueError, match="differ"):
        import_local_sources(tmp_path)


def test_missing_static_weather_never_downloads(tmp_path, monkeypatch):
    monkeypatch.setattr("requests.sessions.Session.request", lambda *a, **k: pytest.fail("Missing snapshot attempted network"))
    with pytest.raises(FileNotFoundError, match="no download"):
        load_cached_history(43.5, 24.1, "2017-04-02", cache_dir=tmp_path)
