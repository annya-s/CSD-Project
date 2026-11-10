"""Offline synthetic comparison models matching the stored 14-day average columns."""
import json
import math
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd
from catboost import CatBoostRegressor
from synthetic_demo import curves

ROOT = Path(__file__).resolve().parent
FOLDER = ROOT / "model/averages"
FEATURES = ["age", "temperature", "soil", "vpd", "sunshine_hours"]
# Explicit scenario assumptions, shared across crops, not crop-optimal recommendations.
REFERENCE = [20.0, .26, 1.0, 8.0]
BOUNDS = [(-10, 40), (0, .6), (0, 5), (0, 24)]
UNITS = {"CARROT": "g/plant dry shoot", "WHEAT": "g/m2 dry aboveground",
         "MAIZE": "g/m2 fresh aboveground", "SOYBEAN": "g/m2 fresh aboveground",
         "SUGAR_BEET": "g/m2 fresh aboveground"}


def train():
    wheat = pd.read_csv(ROOT / "data/processed/observations.csv")
    wheat = wheat.groupby("days_since_sowing").target_dry_biomass_g_m2.median().sort_index()
    sources = [*curves(), ("WHEAT", wheat.index.to_numpy(), wheat.to_numpy())]
    FOLDER.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(42)
    generated = []
    for crop, ages, mass in sources:
        x = np.column_stack([rng.uniform(min(ages), max(ages), 3000),
                             *[rng.uniform(lo, hi, 3000) for lo, hi in BOUNDS]])
        # Synthetic penalties cannot establish causal effects of irrigation or management.
        penalty = .025*abs(x[:, 1]-20) + 2*abs(x[:, 2]-.26) + .12*abs(x[:, 3]-1) + .025*abs(x[:, 4]-8)
        y = np.interp(x[:, 0], ages, mass) * np.clip(1-penalty, .05, 1)
        frame = pd.DataFrame(x, columns=FEATURES)
        model = CatBoostRegressor(iterations=180, depth=5, random_seed=42,
                                  thread_count=2, verbose=False, allow_writing_files=False)
        model.fit(frame.iloc[:2400], y[:2400])
        error = float(np.mean(abs(model.predict(frame.iloc[2400:])-y[2400:])))
        model.save_model(str(FOLDER / f"{crop}.cbm"))
        (FOLDER / f"{crop}.json").write_text(json.dumps(dict(
            profile="historical-averages-v1", age_min=int(min(ages)), age_max=int(max(ages)),
            unit=UNITS[crop], reference=REFERENCE, features=FEATURES,
            synthetic_holdout_mae=error, field_accuracy="unknown")))
        generated.append(frame.assign(crop=crop, biomass=y, origin="SYNTHETIC", unit=UNITS[crop]))
    pd.concat(generated).to_csv(ROOT / "data/synthetic_averages.csv", index=False)


def compare(request):
    try:
        crop = request["crop"]
        if crop not in UNITS:
            return dict(status="UNSUPPORTED", message="No biomass model is available for this crop.")
        meta = json.loads((FOLDER / f"{crop}.json").read_text())
        target = date.fromisoformat(request["prediction_date"])
        start = date.fromisoformat(request["start_date"])
        end = date.fromisoformat(request["end_date"])
        planted = date.fromisoformat(request["sowing_date"])
        if start != target-timedelta(days=14) or end != target-timedelta(days=1):
            raise ValueError()
        age = (target-planted).days
        if age < meta["age_min"] or age+14 > meta["age_max"]:
            return dict(status="UNSUPPORTED", message=f"Plant age is {age} days. A 14-day projection requires age {meta['age_min']}–{meta['age_max']-14} days for this crop.")
        values = [request[k] for k in ["temperature", "soil", "vpd", "sunshine_seconds"]]
        if any(type(v) not in (float, int) or not math.isfinite(v) for v in values):
            raise ValueError()
        values[-1] /= 3600
        if any(not lo <= v <= hi for v, (lo, hi) in zip(values, BOUNDS)):
            raise ValueError()
        model = CatBoostRegressor()
        model.load_model(str(FOLDER / f"{crop}.cbm"))
        frame = pd.DataFrame([[age, *values], [age+14, *values], [age+14, *REFERENCE]], columns=FEATURES)
        now, continued, reference = np.maximum(0, model.predict(frame)).tolist()
        unit = meta["unit"]
        return dict(status="SYNTHETIC_DEMO", value=continued, unit=unit,
                    current_estimate=now, continued_conditions=continued, reference_conditions=reference,
                    projection_date=(target+timedelta(days=14)).isoformat(),
                    message=f"Synthetic biomass comparison ({unit}): estimated now {now:.1f}; in 14 days if recent conditions continue {continued:.1f}; in 14 days under assumed reference conditions {reference:.1f}. Reference: 20 C, soil moisture 0.26 m3/m3, VPD 1 kPa, sunshine 8 h/day. Field accuracy and an agronomic optimum have not been established.")
    except (KeyError, ValueError, TypeError):
        return dict(status="INVALID_INPUT", message="Check planting details and the complete, current 14-day averages.")
    except OSError:
        return dict(status="UNAVAILABLE", message="The average-weather model has not been installed.")


if __name__ == "__main__":
    train()
