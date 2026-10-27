"""Project-local paths and reproducible JSON helpers."""
from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ML_ROOT = Path(__file__).resolve().parents[2]
PROFILE = "ifs-soil-0-7-v1"
FEATURE_NAMES = ["days_since_sowing", "temperature_mean_7d", "temperature_mean_14d",
                 "vpd_mean_7d", "vpd_mean_14d", "soil_moisture_mean_7d",
                 "soil_moisture_mean_14d", "sunshine_hours_mean_7d", "sunshine_hours_mean_14d"]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json(path: str | Path, value) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")


def read_json(path: str | Path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def sha256(path: str | Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def config() -> dict:
    return read_json(ML_ROOT / "config/wheat_v1.json")
