"""Predict from exported database rows by default; live reads and writes opt in separately."""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from .database import DatabaseInputError, SupabaseHistoryReader, request_from_rows
from .persistence import upload_prediction
from .predict import ML_ROOT, Predictor, save_prediction


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--fixture", type=Path, help="Local exported-row JSON (no credentials needed)")
    source.add_argument("--from-database", action="store_true", help="Read configured Supabase Data API")
    parser.add_argument("--farmer-id", type=int)
    parser.add_argument("--planted-at")
    parser.add_argument("--prediction-date")
    parser.add_argument("--artifacts", type=Path)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--upload", action="store_true")
    args = parser.parse_args()
    if args.upload and not args.from_database:
        parser.error("Fixture predictions cannot be uploaded; use trusted live database context")
    if args.from_database and any(x is None for x in (args.farmer_id, args.planted_at, args.prediction_date)):
        parser.error("Live reads require --farmer-id, --planted-at and --prediction-date from a trusted backend")
    try:
        if args.from_database:
            identity = dict(farmer_id=args.farmer_id, planted_at=args.planted_at, prediction_date=args.prediction_date)
            bundle = SupabaseHistoryReader().read(**identity)
        else:
            bundle = json.loads((args.fixture or ML_ROOT / "examples/database_fixture.json").read_text(encoding="utf-8"))
            identity = dict(farmer_id=args.farmer_id if args.farmer_id is not None else bundle["context"]["farmer_id"],
                planted_at=args.planted_at or bundle["context"]["planted_at"],
                prediction_date=args.prediction_date or bundle["prediction_date"])
        request = request_from_rows(bundle, **identity)
        result = Predictor(args.artifacts).predict(request)
        result["input_source"] = "database" if args.from_database else "local_database_fixture"
    except (DatabaseInputError, ValueError, KeyError, TypeError, OSError, RuntimeError, AttributeError) as exc:
        # Fixed codes only; never echo response bodies, URLs, keys or untrusted exceptions.
        result = dict(id=str(uuid4()), schema_version="database-prediction-error-v1", value=None, unit="g/m2",
            status=exc.status if isinstance(exc, DatabaseInputError) else "UNAVAILABLE",
            support_reasons=[exc.reason if isinstance(exc, DatabaseInputError) else "DATABASE_INPUT_UNAVAILABLE"],
            created_at=datetime.now(timezone.utc).isoformat())
    path = save_prediction(result, args.output_dir)
    summary = dict(status=result["status"], value=result["value"], unit=result["unit"], saved_to=str(path))
    if args.upload and result.get("schema_version") == "wheat-prediction-v1":
        try:
            summary.update(upload_prediction(result))
        except (RuntimeError, ValueError):
            summary["upload_error"] = "DATABASE_UPLOAD_UNAVAILABLE"
    print(json.dumps(summary))
    if result["status"] == "UNAVAILABLE" or "upload_error" in summary:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
