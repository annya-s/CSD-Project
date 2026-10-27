"""Fabricated unit-test observations, never scientific training data."""
from datetime import date

import pandas as pd


def observed_fixture():
    rows = []
    for season, sowing, dates in [
        ("S16-17", "2016-10-01", ["2017-03-20", "2017-04-20", "2017-05-20"]),
        ("S17-18", "2017-10-01", ["2017-11-20", "2018-04-20"]),
    ]:
        for field in range(1, 7):
            for day in dates:
                age = (date.fromisoformat(day) - date.fromisoformat(sowing)).days
                rows.append({"visit_id": f"{season}_{field}_{day}", "season": season,
                             "field_season_id": f"{season}_U{field}", "target_date": day,
                             "sowing_date": sowing, "days_since_sowing": age,
                             "target_dry_biomass_g_m2": age * 1.5 + field,
                             "data_origin": "OBSERVED", "feature_profile": "ifs-soil-0-7-v1",
                             "latitude": 43.5 + field / 1000, "longitude": 24.1 + field / 1000,
                             "temperature_mean_7d": 15.0, "temperature_mean_14d": 14.0,
                             "vpd_mean_7d": 0.8, "vpd_mean_14d": 0.7,
                             "soil_moisture_mean_7d": 0.3, "soil_moisture_mean_14d": 0.31,
                             "sunshine_hours_mean_7d": 6.0, "sunshine_hours_mean_14d": 5.5,
                             "weather_window_id": f"grid_{day}"})
    return pd.DataFrame(rows)
