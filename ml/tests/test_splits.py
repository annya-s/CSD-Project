import copy

import pytest

from csd_weather.splits import apply_splits, freeze_splits, make_splits
from training_cases import observed_fixture


def test_grouped_and_temporal_split_is_order_invariant():
    frame = observed_fixture()
    split = make_splits(frame)
    reordered = make_splits(frame.sample(frac=1, random_state=9))
    assert split == reordered
    assigned = apply_splits(frame, split)
    assert assigned.groupby("field_season_id").partition.nunique().max() == 1
    assert assigned.loc[assigned.partition.eq("test"), "season"].eq("S17-18").all()


def test_synthetic_rows_never_enter_real_splits():
    frame = observed_fixture()
    frame.loc[0, "data_origin"] = "SYNTHETIC_SCENARIO"
    with pytest.raises(ValueError, match="OBSERVED"):
        make_splits(frame)


def test_frozen_split_rejects_new_records_and_corrupt_assignments(tmp_path):
    frame = observed_fixture()
    path = tmp_path / "split.json"
    split = freeze_splits(frame, path)
    before = path.read_bytes()
    assert freeze_splits(frame, path, seed=999) == split
    assert path.read_bytes() == before
    with pytest.raises(ValueError, match="does not match"):
        freeze_splits(frame.iloc[1:], path)
    corrupt = copy.deepcopy(split)
    corrupt["assignments"]["nonexistent"] = "test"
    with pytest.raises(ValueError, match="identities"):
        apply_splits(frame, corrupt)


def test_shared_parent_cannot_cross_partitions():
    frame = observed_fixture()
    split = make_splits(frame)
    frame["parent_window_id"] = "shared-parent"
    with pytest.raises(ValueError, match="Parent-window leakage"):
        apply_splits(frame, split)


@pytest.mark.parametrize("mutation", ["schema", "field_seasons"])
def test_frozen_manifest_metadata_is_checked(mutation):
    frame = observed_fixture()
    split = make_splits(frame)
    if mutation == "schema":
        split["schema_version"] = "unknown"
    else:
        split["field_seasons"]["train"] = []
    with pytest.raises(ValueError, match="metadata"):
        apply_splits(frame, split)
