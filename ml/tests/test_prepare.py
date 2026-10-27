"""Parsing checks use tiny fabricated records, never model-training labels."""
import numpy as np
import pandas as pd
import pytest

from csd_weather.prepare import prepare_tables, read_table


def tables():
    biomass = pd.DataFrame([
        {"ESU_ID": "june2017_1_2", "Campaign": "June_2017", "AGB_N_date": "2017-06-20",
         "LP80_date": "2017-06-19", "AGBd_wheat": 123.0, "Point_X_UTM34": 500000,
         "Point_Y_UTM34": 0},
        {"ESU_ID": "nov2017_1_2", "Campaign": "November_2017", "AGB_N_date": "2017-11-20",
         "LP80_date": "2017-11-19", "AGBd_wheat": 25.0, "Point_X_UTM34": 500000,
         "Point_Y_UTM34": 0},
    ])
    calendar = pd.DataFrame([{"Unit_ID": "S16-17_U1", "Sowing_date": "2016-10-01"},
                             {"Unit_ID": "S17-18_U1", "Sowing_date": "2017-10-15"}])
    return biomass, calendar


def test_tsv_preserves_multiline_quotes_and_missing_value(tmp_path):
    path = tmp_path / "calendar.txt"
    path.write_text('Unit_ID\t Notes \tValue\nS16-17_U1\t"first\nsecond"\tNA\n', encoding="utf-8", newline="")
    frame = read_table(path)
    assert frame.columns.tolist() == ["Unit_ID", "Notes", "Value"]
    assert frame.iloc[0].Notes == "first\nsecond"
    assert pd.isna(frame.iloc[0].Value)


def test_join_uses_season_and_biomass_date_and_handles_june():
    result, rejected = prepare_tables(*tables())
    assert not rejected
    first, second = result.to_dict("records")
    assert first["field_season_id"] == "S16-17_U1"
    assert first["target_date"] == "2017-06-20"
    assert first["longitude"] == pytest.approx(21)
    assert first["latitude"] == pytest.approx(0)
    assert second["field_season_id"] == "S17-18_U1"
    assert second["days_since_sowing"] == 36


def test_missing_biomass_date_never_falls_back_to_other_measurement():
    biomass, calendar = tables()
    biomass.loc[0, "AGB_N_date"] = np.nan
    result, rejected = prepare_tables(biomass, calendar)
    assert len(result) == 1
    assert rejected == [{"visit_id": "june2017_1_2", "reason": "MISSING_OR_INVALID_DATE"}]


def test_ambiguous_calendar_join_fails():
    biomass, calendar = tables()
    with pytest.raises(ValueError, match="must be unique"):
        prepare_tables(biomass, pd.concat([calendar, calendar.iloc[[0]]]))


def test_duplicate_visit_fails():
    biomass, calendar = tables()
    with pytest.raises(ValueError, match="Duplicate source visit"):
        prepare_tables(pd.concat([biomass, biomass.iloc[[0]]]), calendar)
