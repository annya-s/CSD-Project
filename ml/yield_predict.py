"""Yield outlook for one planting, used by the Java backend (YieldModelClient).

Input (JSON on stdin, see YieldModelClient): crop, latitude, longitude and the last
14 days' average temperature (°C), soil moisture (m³/m³), vapour pressure deficit
(kPa) and sunshine duration (seconds per day).

Output: the model's yield ratio (1.0 = a normal year) applied to the normal yield of
the nearest training region that grows this crop, in t/ha.
"""
import json
import math
from pathlib import Path

import joblib
import pandas as pd

ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "model/yield"
LOCATIONS = {loc["id"]: loc for loc in
             json.loads((ROOT / "yield_locations.json").read_text(encoding="utf-8"))["locations"]}
MODEL_NAMES = {"ridge": "Ridge regression", "random_forest": "Random Forest", "catboost": "CatBoost"}


def distance_km(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2)
    return 12742 * math.asin(math.sqrt(a))


def predict(request):
    try:
        metadata = json.loads((MODEL_DIR / "metadata.json").read_text(encoding="utf-8"))
        normals = json.loads((MODEL_DIR / "normals.json").read_text(encoding="utf-8"))
        model = joblib.load(MODEL_DIR / "model.joblib")
    except OSError:
        return dict(status="UNAVAILABLE", message="The yield model has not been trained yet.")
    try:
        crop = request["crop"]
        lat, lon = float(request["latitude"]), float(request["longitude"])
        weather = dict(temperature=float(request["temperature"]), soil_moisture=float(request["soil"]),
                       vpd=float(request["vpd"]), sunshine_hours=float(request["sunshine_seconds"]) / 3600)
        if not all(math.isfinite(v) for v in [lat, lon, *weather.values()]):
            raise ValueError()
    except (KeyError, TypeError, ValueError):
        return dict(status="INVALID_INPUT", message="Check the planting location and 14-day weather averages.")
    if crop not in metadata["crops"]:
        return dict(status="UNSUPPORTED", message="No yield model is available for this crop.")

    ranges = metadata["training_ranges"]
    outside = [name for name, value in weather.items()
               if not ranges[name]["min"] <= value <= ranges[name]["max"]]
    if outside:
        labels = {"temperature": "temperature", "soil_moisture": "soil moisture",
                  "vpd": "vapour pressure deficit", "sunshine_hours": "sunshine"}
        names = [labels[n] for n in outside]
        listed = names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]
        return dict(status="OUT_OF_RANGE", message=f"Recent {listed} {'is' if len(names) == 1 else 'are'}"
                    " outside the growing-season conditions the yield model was trained on"
                    " (for example, it may be winter). No yield outlook is shown.")

    nearest = min((n for n in normals if n["crop"] == crop),
                  key=lambda n: distance_km(lat, lon, n["lat"], n["lon"]))
    usual = metadata["climatology"][nearest["location"]]
    # shortcut: compares 14 days with a whole-season average, so early or late in the season
    # the anomalies are biased; upgrade by storing month-by-month climatology.
    anomalies = {f"{name}_anomaly": value - usual[name] for name, value in weather.items()}
    row = pd.DataFrame([dict(crop=crop, **weather, abs_latitude=abs(lat), **anomalies)])[metadata["features"]]
    ratio = float(model.predict(row)[0])
    location = LOCATIONS[nearest["location"]]
    test = metadata["metrics"][metadata["selected_model"]]["test"]
    return dict(status="ML_ESTIMATE", ratio=round(ratio, 4),
                expected_yield=round(ratio * nearest["normal_yield_t_ha"], 2),
                normal_yield=nearest["normal_yield_t_ha"], unit="t/ha",
                region=f"{location['region']}, {location['area']}",
                model=MODEL_NAMES.get(metadata["selected_model"], metadata["selected_model"]),
                message=(f"Machine-learning estimate from FAO national yields and weather for "
                         f"{metadata['locations']} countries, {metadata['years'][0]}–{metadata['years'][1]}. "
                         f"Typical error on unseen years: ±{test['mae_pct']:.0f}% of a normal yield. "
                         "Assumes the last 14 days' weather is typical of the whole season."))


if __name__ == "__main__":
    import sys
    print(json.dumps(predict(json.load(sys.stdin)), allow_nan=False))
