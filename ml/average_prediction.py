"""Offline synthetic comparison models matching the stored 14-day average columns."""
import json
import math
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd
from catboost import CatBoostRegressor

ROOT = Path(__file__).resolve().parent
FOLDER = ROOT / "model/averages"
FEATURES = ["age", "temperature", "soil", "vpd", "sunshine_hours"]
BOUNDS = [(-50, 50), (0, .6), (0, 5), (0, 24)]
PROFILES = json.loads((ROOT / "crop_profiles.json").read_text())


def train():
    FOLDER.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(42)
    generated = []
    for crop, profile in PROFILES.items():
        ages, mass = profile["ages"], profile["biomass"]
        reference = np.array(profile["reference"])
        x = np.column_stack([rng.uniform(min(ages), max(ages), 1000),
                             *[rng.uniform(lo, hi, 1000) for lo, hi in BOUNDS]])
        # Include favourable scenarios explicitly; the other rows cover stresses.
        x[:200, 1:] = reference
        # Include the full-growth stage used by the product comparison.
        x[200:500, 0] = profile["maturity_days"]
        # Synthetic penalties cannot establish causal effects of irrigation or management.
        penalty = np.sum(abs(x[:, 1:] - reference) * [.025, 2, .12, .025], axis=1)
        # Learn a fraction of the assumed mature biomass, with a physical 0..1 bound.
        y = np.interp(x[:, 0], ages, mass) / profile["mature_target"] * np.clip(1-penalty, 0, 1)
        frame = pd.DataFrame(x, columns=FEATURES)
        model = CatBoostRegressor(iterations=180, depth=5, random_seed=42,
                                  thread_count=2, verbose=False, allow_writing_files=False)
        order = rng.permutation(len(frame))
        model.fit(frame.iloc[order[:800]], y[order[:800]])
        error = float(np.mean(abs(model.predict(frame.iloc[order[800:]])-y[order[800:]])))
        model.save_model(str(FOLDER / f"{crop}.cbm"))
        (FOLDER / f"{crop}.json").write_text(json.dumps(dict(
            profile="mature-fraction-v3", age_min=int(min(ages)), age_max=int(max(ages)),
            unit=profile["unit"], reference=profile["reference"], features=FEATURES,
            synthetic_holdout_mae=error, field_accuracy="unknown", soil_moisture_depth_cm="3-9")))
        generated.append(frame.assign(crop=crop, biomass=y*profile["mature_target"], biomass_fraction=y,
                                      origin="SYNTHETIC", unit=profile["unit"], soil_moisture_depth_cm="3-9",
                                      split=np.where(np.isin(np.arange(len(frame)), order[:800]), "train", "test")))
    (ROOT / "data").mkdir(exist_ok=True)
    pd.concat(generated).to_csv(ROOT / "data/synthetic_averages.csv", index=False)


def compare(request):
    try:
        crop = request["crop"]
        if crop not in PROFILES:
            return dict(status="UNSUPPORTED", message="No biomass model is available for this crop.")
        profile = PROFILES[crop]
        target = date.fromisoformat(request["prediction_date"])
        planted = date.fromisoformat(request["sowing_date"])
        age = (target-planted).days
        maturity = profile["maturity_days"]
        if age < 0 or age > maturity:
            return dict(status="UNSUPPORTED", message=f"Plant age is {age} days. This crop's full-growth scenario supports planting ages 0–{maturity} days.")
        # A planning reference comes from the documented curve, not live weather.
        reference = float(profile["mature_target"])
        maturity_date = (planted+timedelta(days=maturity)).isoformat()
        if request.get("mode") == "reference":
            return dict(status="REFERENCE_ONLY", value=None, unit=profile["unit"],
                        reference_conditions=reference,
                        projection_date=maturity_date,
                        message="Full-growth target available; weather prediction unavailable.")
        start = date.fromisoformat(request["start_date"])
        end = date.fromisoformat(request["end_date"])
        if start != target-timedelta(days=14) or end != target-timedelta(days=1):
            raise ValueError()
        meta = json.loads((FOLDER / f"{crop}.json").read_text())
        if meta.get("profile") != "mature-fraction-v3":
            return dict(status="UNAVAILABLE", message="Rebuild the crop models with average_prediction.py.")
        values = [request[k] for k in ["temperature", "soil", "vpd", "sunshine_seconds"]]
        if any(type(v) not in (float, int) or not math.isfinite(v) for v in values):
            raise ValueError()
        values[-1] /= 3600
        if any(not lo <= v <= hi for v, (lo, hi) in zip(values, BOUNDS)):
            raise ValueError()
        model = CatBoostRegressor()
        model.load_model(str(FOLDER / f"{crop}.cbm"))
        reference_weather = meta["reference"]
        frame = pd.DataFrame([[age, *values], [maturity, *values]], columns=FEATURES)
        now, continued = (np.clip(model.predict(frame), 0, 1)*reference).tolist()
        unit = meta["unit"]
        return dict(status="SYNTHETIC_DEMO", value=continued, unit=unit,
                    current_estimate=now, continued_conditions=continued, reference_conditions=reference,
                    projection_date=maturity_date,
                    message=f"Optimal full-growth scenario at {maturity} days after planting. Recent 14-day average weather is assumed to continue.")
    except (KeyError, ValueError, TypeError):
        return dict(status="INVALID_INPUT", message="Check planting details and the complete, current 14-day averages.")
    except OSError:
        return dict(status="UNAVAILABLE", message="The average-weather model has not been installed.")


if __name__ == "__main__":
    train()
