"""One offline entry point: local tables -> weather -> features -> splits -> scenarios."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from .common import ML_ROOT, read_json, sha256, write_json
from .features import create_feature_table
from .prepare import prepare
from .splits import freeze_splits
from .synthetic import generate_scenarios, write_scenarios


def preprocess(root: Path = ML_ROOT, *, include_synthetic: bool = True) -> dict:
    """Use only files already under root; missing snapshots are explicitly rejected."""
    root = Path(root)
    observations = prepare(root)
    window_path = root / "data/processed/weather_windows.json"
    windows = read_json(window_path)
    if not isinstance(windows, dict) or not windows:
        raise ValueError("A nonempty static weather snapshot is required")
    features = create_feature_table(root)
    splits = freeze_splits(features, root / "data/splits/wheat_v1.json")
    result = {"source_mode": "STATIC_LOCAL_FILES_ONLY", "observations": len(observations),
              "weather_windows": len(windows), "weather_snapshot_sha256": sha256(window_path),
              "feature_rows": len(features), "synthetic_scenarios": 0}
    if include_synthetic:
        scenarios, manifest = generate_scenarios(features, splits, windows,
            read_json(root / "config/synthetic_scenarios_v1.json"))
        write_scenarios(scenarios, manifest, root / "data/synthetic")
        result["synthetic_scenarios"] = len(scenarios)
    write_json(root / "data/processed/pipeline_audit.json", result)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-synthetic", action="store_true", help="Only rebuild observed features and frozen splits")
    args = parser.parse_args()
    print(json.dumps(preprocess(include_synthetic=not args.skip_synthetic), indent=2))


if __name__ == "__main__":
    main()
