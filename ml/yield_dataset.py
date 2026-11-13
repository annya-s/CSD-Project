"""Build the yield training table: FAOSTAT yields + season weather from Open-Meteo.

Usage (from the project root):
    ml\\.venv\\Scripts\\python.exe ml\\yield_dataset.py fetch   # download weather (cached, throttled)
    ml\\.venv\\Scripts\\python.exe ml\\yield_dataset.py build   # write ml/data/yield_training.csv

Each row is one crop, one country and one harvest year. The label is the yield ratio:
actual FAOSTAT yield divided by that country's long-term yield trend. A ratio of 0.9
means the year came in 10% below what the trend predicted, which removes most of the
effect of technology and management and leaves mainly the weather signal.

The four features match the app's weather inputs, averaged over the growing season:
temperature (°C), soil moisture (m³/m³), vapour pressure deficit (kPa) and sunshine
(hours/day). Absolute latitude is added as a coarse climate-zone hint.
"""
import json
import math
import sys
import time
import zipfile
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
import requests

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "data/raw"
WEATHER_CACHE = RAW / "open_meteo_seasons"
FAOSTAT_ZIP = RAW / "faostat/production_normalized.zip"
OUTPUT = ROOT / "data/yield_training.csv"
LOCATIONS = json.loads((ROOT / "yield_locations.json").read_text(encoding="utf-8"))["locations"]

FIRST_YEAR, LAST_YEAR = 2011, 2024
TREND_FROM = 2000  # the yield trend is fitted on more years than the weather covers

# App crop code -> FAOSTAT item name.
CROPS = {
    "WHEAT": "Wheat", "MAIZE": "Maize (corn)", "RICE": "Rice", "POTATO": "Potatoes",
    "SUGAR_CANE": "Sugar cane", "APPLE": "Apples", "TOMATO": "Tomatoes",
    "CARROT": "Carrots and turnips", "LETTUCE": "Lettuce and chicory", "SOYBEAN": "Soya beans",
}
FEATURES = ["temperature", "soil_moisture", "vpd", "sunshine_hours", "abs_latitude"]
DAILY = "temperature_2m_mean,relative_humidity_2m_mean,soil_moisture_0_to_7cm_mean,sunshine_duration"
SECONDS_BETWEEN_REQUESTS = 12  # about 13 weighted calls per request: stays under 5,000 per hour


def season(lat, year):
    """Six-month main growing season ending in the harvest year, by hemisphere."""
    if lat >= 23.5:
        return date(year, 4, 1), date(year, 9, 30)      # northern temperate: April-September
    if lat >= 0:
        return date(year, 5, 1), date(year, 10, 31)     # northern tropics: main wet season
    if lat > -23.5:
        return date(year - 1, 11, 1), date(year, 4, 30)  # southern tropics
    return date(year - 1, 10, 1), date(year, 3, 31)      # southern temperate


def cache_path(location, year):
    return WEATHER_CACHE / f"{location['id']}_{year}.json"


def fetch():
    WEATHER_CACHE.mkdir(parents=True, exist_ok=True)
    todo = [(loc, year) for loc in LOCATIONS for year in range(FIRST_YEAR, LAST_YEAR + 1)
            if not cache_path(loc, year).exists()]
    print(f"{len(todo)} season requests to fetch", flush=True)
    for done, (loc, year) in enumerate(todo, 1):
        start, end = season(loc["lat"], year)
        params = dict(latitude=loc["lat"], longitude=loc["lon"], start_date=start.isoformat(),
                      end_date=end.isoformat(), daily=DAILY, timezone="GMT")
        for attempt in range(6):
            response = requests.get("https://archive-api.open-meteo.com/v1/archive", params=params, timeout=60)
            if response.status_code == 200:
                cache_path(loc, year).write_text(response.text, encoding="utf-8")
                break
            wait = 60 * (attempt + 1)
            print(f"HTTP {response.status_code} for {loc['id']} {year}; retrying in {wait}s", flush=True)
            time.sleep(wait)
        else:
            raise SystemExit(f"Giving up on {loc['id']} {year}")
        if done % 25 == 0:
            print(f"{done}/{len(todo)} fetched", flush=True)
        time.sleep(SECONDS_BETWEEN_REQUESTS)
    print("All weather fetched", flush=True)


def vpd_kpa(temperature_c, humidity_pct):
    """Vapour pressure deficit from daily mean temperature and humidity (Tetens equation)."""
    saturation = 0.6108 * np.exp(17.27 * temperature_c / (temperature_c + 237.3))
    return saturation * (1 - humidity_pct / 100)


def season_weather(location, year):
    daily = json.loads(cache_path(location, year).read_text(encoding="utf-8"))["daily"]
    frame = pd.DataFrame(daily).drop(columns="time").astype(float)
    if frame.isna().mean().max() > 0.1:  # skip seasons with more than 10% missing days
        return None
    t = frame.temperature_2m_mean
    return dict(temperature=t.mean(), soil_moisture=frame.soil_moisture_0_to_7cm_mean.mean(),
                vpd=vpd_kpa(t, frame.relative_humidity_2m_mean).mean(),
                sunshine_hours=frame.sunshine_duration.mean() / 3600)


def faostat_yields():
    with zipfile.ZipFile(FAOSTAT_ZIP) as archive:
        name = next(n for n in archive.namelist() if n.endswith("(Normalized).csv"))
        table = pd.read_csv(archive.open(name), encoding="latin-1",
                            usecols=["Area Code", "Item", "Element", "Year", "Value"])
    codes = {loc["area_code"] for loc in LOCATIONS}
    items = {v: k for k, v in CROPS.items()}
    table = table[table.Element.eq("Yield") & table["Area Code"].isin(codes)
                  & table.Item.isin(items) & table.Year.ge(TREND_FROM)].dropna(subset=["Value"])
    return table.assign(crop=table.Item.map(items), yield_t_ha=table.Value / 1000)


def build():
    yields = faostat_yields()
    by_code = {loc["area_code"]: loc for loc in LOCATIONS}
    rows, normals = [], []
    for (code, crop), group in yields.groupby(["Area Code", "crop"]):
        group = group[group.yield_t_ha > 0].sort_values("Year")
        if len(group) < 15:  # need enough years for a trend
            continue
        slope, intercept = np.polyfit(group.Year, group.yield_t_ha, 1)
        location = by_code[code]
        normals.append(dict(crop=crop, location=location["id"], lat=location["lat"], lon=location["lon"],
                            normal_yield_t_ha=round(float(slope * LAST_YEAR + intercept), 3)))
        for record in group[group.Year.between(FIRST_YEAR, LAST_YEAR)].itertuples():
            trend = slope * record.Year + intercept
            if trend <= 0 or not cache_path(location, record.Year).exists():
                continue
            weather = season_weather(location, record.Year)
            if weather is None:
                continue
            rows.append(dict(crop=crop, location=location["id"], year=int(record.Year),
                             abs_latitude=abs(location["lat"]), **weather,
                             yield_t_ha=record.yield_t_ha, trend_t_ha=trend,
                             yield_ratio=record.yield_t_ha / trend))
    frame = pd.DataFrame(rows)
    frame.to_csv(OUTPUT, index=False)
    (ROOT / "data/yield_normals.json").write_text(json.dumps(normals, indent=1), encoding="utf-8")
    print(f"{len(frame)} rows, {frame.location.nunique()} locations, crops: "
          f"{frame.crop.value_counts().to_dict()}")


if __name__ == "__main__":
    {"fetch": fetch, "build": build}[sys.argv[1]]()
