"""Generate source-anchored synthetic scenarios and train offline demonstration models."""
from pathlib import Path
import json
import zipfile
import numpy as np
import pandas as pd
from catboost import CatBoostRegressor
from data_preprocessing import build_features

ROOT = Path(__file__).resolve().parent
FEATURES = ["days_since_sowing", "temperature_min_mean_14d", "temperature_max_mean_14d",
            "soil_moisture_mean_14d", "solar_radiation_total_14d"]
BONN = {"MAIZE": ("Maize", "2023-05-18"), "SOYBEAN": ("Soy Bean", "2023-05-05")}
# Hypothetical 14-day summaries. Their biomass effects are teaching assumptions.
SCENARIOS = {"mild": [12, 24, .26, 235], "hot-dry": [21, 35, .12, 315],
             "cool": [5, 15, .30, 145]}
FOLDER = ROOT / "model" / "synthetic"


def curves():
    raw = ROOT / "data/raw"
    carrot = pd.to_numeric(pd.read_csv(raw / "carrot/diallelRawData.csv").dlw, errors="coerce").dropna()
    # Only the harvest endpoint is measured. Earlier ages are a hypothetical growth curve.
    yield "CARROT", [14, 65, 130], [carrot.median() * .03, carrot.median() * .31, carrot.median()]
    with zipfile.ZipFile(raw / "must_c/metadata.zip") as archive:
        table = pd.read_csv(archive.open("LAI_biomass_and_metadata/md_Biomass.csv"))
    table = table[table.Dont_use_psqm.fillna(0).ne(1)].copy()
    table["date"] = pd.to_datetime(table.date.astype(str), format="%Y%m%d")
    for crop, (name, sowing) in BONN.items():
        observations = table[table.crop.eq(name)].groupby("date")["biomass per full plot (g/m2)"].median().dropna()
        yield crop, (observations.index - pd.Timestamp(sowing)).days.to_numpy(), observations.to_numpy()


def train():
    rows = []
    rng = np.random.default_rng(42)
    FOLDER.mkdir(parents=True, exist_ok=True)
    for crop, ages, masses in curves():
        if len(ages) < 2:
            raise ValueError(f"Insufficient dated biomass for {crop}")
        age = rng.integers(min(ages), max(ages) + 1, 1500)
        minimum = rng.uniform(5, 22, len(age))
        maximum = minimum + rng.uniform(9, 15, len(age))
        soil = rng.uniform(.10, .34, len(age))
        solar = rng.uniform(135, 330, len(age))
        frame = pd.DataFrame(dict(zip(FEATURES, [age, minimum, maximum, soil, solar])))
        # Relative penalties are invented, not inferred agronomic response functions.
        scale = np.clip(1 - .015 * np.abs(maximum - 24) - 1.5 * np.maximum(.26 - soil, 0)
                        - .0008 * np.maximum(235 - solar, 0), .25, 1)
        target = np.interp(age, ages, masses) * scale
        model = CatBoostRegressor(iterations=120, depth=4, random_seed=42, thread_count=2,
                                  verbose=False, allow_writing_files=False)
        model.fit(frame, target)
        model.save_model(str(FOLDER / (crop + ".cbm")))
        (FOLDER / (crop + ".json")).write_text(json.dumps([int(min(ages)), int(max(ages))]))
        rows.append(frame.assign(crop=crop, origin="SYNTHETIC", biomass=target,
                    unit="g/plant dry shoot" if crop == "CARROT" else "g/m2 fresh aboveground"))
    pd.concat(rows).to_csv(ROOT / "data/synthetic_biomass.csv", index=False)


def simulate(request):
    crop = request["crop"]
    if crop not in {"CARROT", *BONN}:
        return {"status": "INVALID_INPUT"}
    lo, hi = json.loads((FOLDER / (crop + ".json")).read_text())
    live = "weather" in request
    if live:
        try:
            features = build_features(request, expected_crop=crop)
        except ValueError:
            return {"status": "INVALID_INPUT"}
        age = features["days_since_sowing"]
        vector = [features[key] for key in FEATURES]
        bounds = [(5, 22), (14, 37), (.10, .34), (135, 330)]
        if any(not low <= value <= high for value, (low, high) in zip(vector[1:], bounds)):
            return {"status": "UNSUPPORTED", "message": "Recent weather falls outside this synthetic model's training ranges."}
    else:
        if request.get("scenario") not in SCENARIOS:
            return {"status": "INVALID_INPUT"}
        age = request["age"]
        vector = [age, *SCENARIOS[request["scenario"]]]
    if type(age) != int or not lo <= age <= hi:
        return {"status": "UNSUPPORTED", "message": f"Plant age is {age} days. This synthetic model supports ages {lo} to {hi} days."}
    model = CatBoostRegressor()
    model.load_model(str(FOLDER / (crop + ".cbm")))
    value = float(model.predict(pd.DataFrame([vector], columns=FEATURES))[0])
    unit = "g/plant dry shoot" if crop == "CARROT" else "g/m² fresh aboveground"
    label = "Synthetic-model estimate using recorded weather" if live else "Simulated biomass"
    return {"status": "SYNTHETIC_DEMO", "value": value, "unit": unit,
            "message": f"{label} at day {age}: {value:.1f} {unit}. Trained on synthetic data; field accuracy has not been established."}


if __name__ == "__main__":
    train()
