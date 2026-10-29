"""Predict locally from a validated historical window; never writes to Supabase."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import pickle
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import joblib
import pandas as pd
from catboost import CatBoostRegressor
from catboost import CatBoostError

from .advice import weather_advice
from .common import INVALID_INPUT_MESSAGE
from .contracts import PredictionRequest
from .features import build_features
from .common import FEATURE_NAMES

ML_ROOT = Path(__file__).resolve().parents[2]


def canonical_json(value: dict) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


class Predictor:
    def __init__(self, artifact_dir: str | Path | None = None):
        self.directory = Path(artifact_dir or os.getenv("ML_ARTIFACT_DIR", str(ML_ROOT / "artifacts/wheat_biomass_v1"))).resolve()
        self.model = None
        self.error = None
        try:
            self.selection = json.loads((self.directory / "selected_model.json").read_text(encoding="utf-8"))
            self.schema = json.loads((self.directory / "feature_schema.json").read_text(encoding="utf-8"))
            self.card = json.loads((self.directory / "model_card.json").read_text(encoding="utf-8"))
            model_file = (self.directory / self.selection["model_file"]).resolve()
            if not model_file.is_relative_to(self.directory):
                raise ValueError("Model path must remain inside artifact directory")
            payload = model_file.read_bytes()
            self.model_hash = hashlib.sha256(payload).hexdigest()
            if self.model_hash != self.selection["artifact_sha256"]:
                raise ValueError("Artifact checksum does not match the selected model")
            if self.selection["feature_profile"] != self.schema["feature_profile"]:
                raise ValueError("Artifact feature profiles disagree")
            if self.card["model_version"] != self.selection["model_version"]:
                raise ValueError("Artifact model versions disagree")
            if self.card["feature_profile"] != self.schema["feature_profile"] or self.card["selected_model"] != self.selection["selected_model"]:
                raise ValueError("Model card does not describe the selected artifact")
            if self.schema["schema_version"] != "wheat-features-v1" or self.schema["feature_names"] != FEATURE_NAMES:
                raise ValueError("Unsupported feature schema")
            if self.schema["unit"] != "g/m2" or self.card["unit"] != "g/m2" or self.card["kind"] != "DRY_ABOVEGROUND_BIOMASS":
                raise ValueError("Unsupported prediction target")
            if not self.selection["input_features"] or not set(self.selection["input_features"]).issubset(self.schema["feature_names"]):
                raise ValueError("Artifact feature names disagree")
            scope = self.schema["scope"]
            if scope["crop"] != "winter_wheat":
                raise ValueError("Unsupported artifact crop")
            for prefix in ("latitude", "longitude"):
                low, high = scope[f"{prefix}_min"], scope[f"{prefix}_max"]
                if not math.isfinite(low) or not math.isfinite(high) or low > high:
                    raise ValueError("Invalid artifact geographic bounds")
            for name in FEATURE_NAMES:
                bounds = self.schema["training_ranges"][name]
                if not math.isfinite(bounds["min"]) or not math.isfinite(bounds["max"]) or bounds["min"] > bounds["max"]:
                    raise ValueError("Invalid artifact feature bounds")
            if self.selection["model_format"] == "catboost":
                model = CatBoostRegressor()
                model.load_model(str(model_file))
            elif self.selection["model_format"] == "joblib":
                # Only load locally produced trusted artifacts; pickle/joblib is not a safe upload format.
                model = joblib.load(model_file)
            else:
                raise ValueError("Unsupported model format")
            self.model = model
        except (OSError, ValueError, KeyError, TypeError, EOFError, ImportError, AttributeError, IndexError, pickle.UnpicklingError, CatBoostError) as exc:
            self.error = type(exc).__name__
            self.selection = {"model_version": "unavailable", "selected_model": None}
            self.schema = {}
            self.card = {}
            self.model_hash = None

    @property
    def ready(self) -> bool:
        return self.model is not None

    def predict(self, request: PredictionRequest) -> dict:
        payload = request.model_dump(mode="json")
        input_hash = hashlib.sha256(canonical_json(payload).encode()).hexdigest()
        result = {
            "schema_version": "wheat-prediction-v1", "kind": "DRY_ABOVEGROUND_BIOMASS",
            "status": "UNAVAILABLE", "validation_status": "EXPERIMENTAL",
            "prediction_date": request.prediction_date.isoformat(),
            "input_through_date": (request.prediction_date - timedelta(days=1)).isoformat(),
            "value": None, "unit": "g/m2", "interval": None, "confidence": None,
            "model_version": self.selection["model_version"],
            "selected_model": self.selection["selected_model"], "model_sha256": self.model_hash,
            "feature_profile": self.schema.get("feature_profile"), "input_hash": input_hash,
            "farmer_id": request.farmer_id, "crop_type": "WHEAT" if request.crop == "winter_wheat" else request.crop,
            "planted_at": request.planted_at.isoformat() if request.planted_at else None,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "support_reasons": [], "features": None,
            "advice": weather_advice(None),
        }
        result["id"] = str(uuid5(NAMESPACE_URL, f"csd-weather:{input_hash}:{self.model_hash}:{result['model_version']}"))
        if not self.ready:
            result["support_reasons"] = ["MODEL_UNAVAILABLE"]
            return result
        if request.crop != "winter_wheat" or request.growing_system != "outdoor_soil":
            result.update(status="UNSUPPORTED", support_reasons=["UNSUPPORTED_CROP_OR_GROWING_SYSTEM"])
            return result
        try:
            zone = ZoneInfo(request.history["timezone"])
            if request.prediction_date > datetime.now(zone).date():
                result.update(status="UNSUPPORTED", support_reasons=["FUTURE_PREDICTION_DATE"])
                return result
            if not math.isclose(float(request.history["latitude"]), request.latitude, abs_tol=1e-6) or not math.isclose(float(request.history["longitude"]), request.longitude, abs_tol=1e-6):
                raise ValueError("History location differs from requested field")
            features = build_features(request.history, request.prediction_date, request.sowing_date, self.schema["feature_profile"])
            if set(features) != set(self.schema["feature_names"]):
                raise ValueError("Feature schema mismatch")
        except (ValueError, KeyError, TypeError, AttributeError, IndexError, OverflowError, ZoneInfoNotFoundError):
            result.update(status="INVALID_INPUT", support_reasons=["INVALID_INPUT"], message=INVALID_INPUT_MESSAGE)
            return result
        result["features"] = features
        result["advice"] = weather_advice(features)
        scope = self.schema["scope"]
        reasons = []
        if not (scope["latitude_min"] <= request.latitude <= scope["latitude_max"] and scope["longitude_min"] <= request.longitude <= scope["longitude_max"]):
            reasons.append("OUTSIDE_TRAINING_REGION")
        for name, value in features.items():
            bounds = self.schema["training_ranges"][name]
            if not math.isfinite(value) or not bounds["min"] <= value <= bounds["max"]:
                reasons.append(f"OUTSIDE_TRAINING_RANGE:{name}")
        if reasons:
            result.update(status="UNSUPPORTED", support_reasons=reasons)
            return result
        frame = pd.DataFrame([features])[self.selection["input_features"]]
        try:
            prediction = float(self.model.predict(frame)[0])
        except (ValueError, TypeError, IndexError, CatBoostError):
            result.update(status="UNAVAILABLE", support_reasons=["MODEL_INFERENCE_FAILED"])
            return result
        if not math.isfinite(prediction):
            result.update(status="UNAVAILABLE", support_reasons=["NONFINITE_MODEL_OUTPUT"])
            return result
        result.update(status="EXPERIMENTAL", value=max(float(self.selection.get("clip_min", 0)), prediction),
                      support_reasons=["LIMITED_REGIONAL_VALIDATION", "RETROSPECTIVE_WEATHER_INPUTS"])
        if self.card.get("weather_holdout_gate") != "PASS_EXPLORATORY":
            result["support_reasons"].append("WEATHER_IMPROVEMENT_NOT_ESTABLISHED")
        return result


def save_prediction(result: dict, directory: str | Path | None = None) -> Path:
    directory = Path(directory or os.getenv("ML_PREDICTION_DIR", str(ML_ROOT / "predictions")))
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{result['id']}.json"
    path.write_text(json.dumps(result, indent=2, allow_nan=False), encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", required=True, type=Path)
    parser.add_argument("--artifacts", type=Path)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    try:
        request = PredictionRequest.model_validate_json(args.request.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        print(json.dumps({"status": "INVALID_INPUT", "message": INVALID_INPUT_MESSAGE}))
        raise SystemExit(1) from None
    result = Predictor(args.artifacts).predict(request)
    path = save_prediction(result, args.output_dir)
    summary = {"status": result["status"], "value": result["value"], "unit": result["unit"], "saved_to": str(path)}
    if result["status"] == "INVALID_INPUT":
        summary["message"] = INVALID_INPUT_MESSAGE
    print(json.dumps(summary))
    if result["status"] == "INVALID_INPUT":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
