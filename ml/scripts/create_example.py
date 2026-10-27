"""Build an offline demonstration from a known training window, not a live farmer."""
from csd_weather.common import ML_ROOT, read_json, write_json
from csd_weather.contracts import PredictionRequest
from csd_weather.predict import Predictor
import pandas as pd


def main():
    rows = pd.read_csv(ML_ROOT / "data/processed/observations.csv")
    splits = read_json(ML_ROOT / "data/splits/wheat_v1.json")["assignments"]
    windows = read_json(ML_ROOT / "data/processed/weather_windows.json")
    predictor = Predictor()
    for row in rows.sort_values("visit_id").to_dict("records"):
        if splits.get(row["visit_id"]) != "train":
            continue
        payload = {"crop": "winter_wheat", "growing_system": "outdoor_soil",
                   "sowing_date": row["sowing_date"], "prediction_date": row["target_date"],
                   "latitude": row["latitude"], "longitude": row["longitude"],
                   "history": windows[row["visit_id"]]}
        result = predictor.predict(PredictionRequest.model_validate(payload))
        if result["status"] != "EXPERIMENTAL":
            continue
        write_json(ML_ROOT / "examples/request.json", payload)
        write_json(ML_ROOT / "examples/prediction.json", result)
        write_json(ML_ROOT / "examples/provenance.json", {
            "visit_id": row["visit_id"], "partition": "train",
            "purpose": "Offline API contract demonstration, not new evaluation evidence or a real user prediction"})
        print(f"Example {row['visit_id']}: {result['status']}, {result['value']:.2f} {result['unit']}")
        return
    raise RuntimeError("No supported demonstration window or ready artifact")


if __name__ == "__main__":
    main()
