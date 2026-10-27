"""Freeze field-season splits before tuning; never split repeated visits at random."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import pandas as pd

from .common import ML_ROOT

TARGET = "target_dry_biomass_g_m2"
TRAIN_SEASON = "S16-17"
TEST_SEASON = "S17-18"


def validate_observed(frame: pd.DataFrame) -> None:
    required = {"visit_id", "field_season_id", "season", "target_date", "data_origin", TARGET}
    missing = required - set(frame.columns)
    if missing:
        raise ValueError(f"Missing observed columns: {sorted(missing)}")
    if frame.empty or not frame["data_origin"].eq("OBSERVED").all():
        raise ValueError("Biomass training/evaluation requires OBSERVED records only")
    if frame[list(required)].isna().any().any():
        raise ValueError("Missing observed identifier, date, origin or biomass")
    if frame["visit_id"].duplicated().any():
        raise ValueError("Duplicate visit_id")
    values = pd.to_numeric(frame[TARGET], errors="coerce").to_numpy(dtype=float)
    if not np.isfinite(values).all() or (values < 0).any():
        raise ValueError("Biomass must be finite and nonnegative")
    if frame.groupby("field_season_id")["season"].nunique().gt(1).any():
        raise ValueError("A field-season cannot span seasons")
    pd.to_datetime(frame["target_date"], errors="raise")


def dataset_fingerprint(frame: pd.DataFrame) -> str:
    # Bind the frozen split to record identities and dates, not to row order.
    columns = ["visit_id", "field_season_id", "season", "target_date"]
    payload = frame[columns].astype(str).sort_values("visit_id").to_csv(index=False)
    return hashlib.sha256(payload.encode()).hexdigest()


def make_splits(frame: pd.DataFrame, seed: int = 42, validation_fraction: float = 0.33) -> dict:
    validate_observed(frame)
    if not 0 < validation_fraction < 1:
        raise ValueError("validation_fraction must be between zero and one")
    if set(frame.season) != {TRAIN_SEASON, TEST_SEASON}:
        raise ValueError("Expected the two documented wheat seasons")
    first = frame[frame.season.eq(TRAIN_SEASON)]
    second = frame[frame.season.eq(TEST_SEASON)]
    if pd.to_datetime(first.target_date).max() >= pd.to_datetime(second.target_date).min():
        raise ValueError("Temporal holdout must occur strictly after development records")
    groups = sorted(first.field_season_id.unique())
    if len(groups) < 3:
        raise ValueError("Need at least three development field-season groups")
    shuffled = np.random.default_rng(seed).permutation(groups)
    count = max(1, min(len(groups) - 1, int(np.ceil(len(groups) * validation_fraction))))
    validation = set(shuffled[:count])
    assignments = {}
    for row in frame.itertuples():
        partition = "test" if row.season == TEST_SEASON else (
            "validation" if row.field_season_id in validation else "train")
        assignments[str(row.visit_id)] = partition
    manifest = {
        "schema_version": "wheat-splits-v1", "seed": seed,
        "method": "fixed_grouped_validation_then_temporal_holdout",
        "train_season": TRAIN_SEASON, "test_season": TEST_SEASON,
        "validation_fraction": validation_fraction,
        "dataset_fingerprint": dataset_fingerprint(frame),
        "assignments": dict(sorted(assignments.items())),
        "field_seasons": {
            part: sorted(frame.loc[frame.visit_id.astype(str).map(assignments).eq(part),
                                   "field_season_id"].unique().tolist())
            for part in ("train", "validation", "test")},
        "limitations": ["Adjacent fields may share weather and management",
                        "Temporal holdout is one region and November/April only"],
    }
    apply_splits(frame, manifest)
    return manifest


def apply_splits(frame: pd.DataFrame, manifest: dict) -> pd.DataFrame:
    validate_observed(frame)
    if (manifest.get("schema_version") != "wheat-splits-v1"
            or manifest.get("method") != "fixed_grouped_validation_then_temporal_holdout"
            or manifest.get("train_season") != TRAIN_SEASON
            or manifest.get("test_season") != TEST_SEASON):
        raise ValueError("Unsupported frozen split metadata")
    if manifest["dataset_fingerprint"] != dataset_fingerprint(frame):
        raise ValueError("Frozen split does not match input records")
    if set(manifest["assignments"]) != set(frame.visit_id.astype(str)):
        raise ValueError("Frozen split has extra or missing record identities")
    result = frame.copy()
    result["partition"] = result.visit_id.astype(str).map(manifest["assignments"])
    if result.partition.isna().any() or set(result.partition) != {"train", "validation", "test"}:
        raise ValueError("Incomplete split assignments")
    if result.groupby("field_season_id").partition.nunique().gt(1).any():
        raise ValueError("Field-season leakage across partitions")
    actual_groups = {part: sorted(result.loc[result.partition.eq(part), "field_season_id"].unique().tolist())
                     for part in ("train", "validation", "test")}
    if manifest.get("field_seasons") != actual_groups:
        raise ValueError("Frozen split field-season metadata disagrees with assignments")
    if not result.loc[result.partition.eq("test"), "season"].eq(TEST_SEASON).all():
        raise ValueError("Wrong season in test holdout")
    if not result.loc[~result.partition.eq("test"), "season"].eq(TRAIN_SEASON).all():
        raise ValueError("Test season leaked into development")
    for column in ("parent_window_id", "parent_visit_id"):
        if column in result and result.dropna(subset=[column]).groupby(column).partition.nunique().gt(1).any():
            raise ValueError("Parent-window leakage across partitions")
    development = pd.to_datetime(result.loc[~result.partition.eq("test"), "target_date"])
    held_out = pd.to_datetime(result.loc[result.partition.eq("test"), "target_date"])
    if development.max() >= held_out.min():
        raise ValueError("Temporal holdout overlaps development dates")
    return result


def freeze_splits(frame: pd.DataFrame, path: str | Path, seed: int = 42) -> dict:
    path = Path(path)
    if path.exists():
        manifest = json.loads(path.read_text(encoding="utf-8"))
        apply_splits(frame, manifest)
        return manifest
    manifest = make_splits(frame, seed)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive create prevents accidentally replacing a previously frozen holdout.
    with path.open("x", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--features", type=Path, default=ML_ROOT / "data/processed/features.csv")
    parser.add_argument("--output", type=Path, default=ML_ROOT / "data/splits/wheat_v1.json")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    manifest = freeze_splits(pd.read_csv(args.features), args.output, args.seed)
    print(json.dumps({"split_file": str(args.output), "field_seasons": manifest["field_seasons"]}))


if __name__ == "__main__":
    main()
