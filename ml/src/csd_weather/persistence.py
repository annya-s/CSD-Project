"""Explicit, opt-in server-side export of a locally saved prediction to Supabase."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlparse
from uuid import UUID

import requests


def prediction_row(prediction: dict) -> dict:
    if prediction.get("input_source") == "local_database_fixture":
        raise ValueError("Fixture predictions cannot be uploaded")
    UUID(prediction["id"])
    farmer_id = prediction.get("farmer_id")
    if isinstance(farmer_id, bool) or not isinstance(farmer_id, int) or not 0 < farmer_id <= 9223372036854775807:
        raise ValueError("A trusted backend farmer_id is required for upload")
    if not prediction.get("planted_at"):
        raise ValueError("The owned planting's planted_at timestamp is required for upload")
    if prediction.get("schema_version") != "wheat-prediction-v1":
        raise ValueError("Unsupported prediction schema")
    return {key: prediction[key] for key in (
        "id", "farmer_id", "crop_type", "planted_at", "prediction_date", "model_version", "status", "created_at"
    )} | {"payload": prediction}


def upload_prediction(prediction: dict, *, environment: dict | None = None, session=None) -> dict:
    env = environment if environment is not None else os.environ
    if env.get("SUPABASE_ENABLED", "false").lower() != "true":
        raise RuntimeError("Supabase export is disabled; set SUPABASE_ENABLED=true only after setup")
    url = env.get("SUPABASE_URL", "").rstrip("/")
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in {"", "/"}:
        raise ValueError("SUPABASE_URL must be an HTTPS project origin")
    secret = env.get("SUPABASE_SECRET_KEY", "")
    legacy = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    headers = {"Content-Type": "application/json", "Prefer": "resolution=ignore-duplicates,return=minimal"}
    # https://supabase.com/docs/guides/getting-started/api-keys
    # Modern sb_secret keys belong in apikey, not Authorization: Bearer (they are not JWTs).
    if secret:
        if not secret.startswith("sb_secret_"):
            raise ValueError("SUPABASE_SECRET_KEY must be a server secret key")
        headers["apikey"] = secret
    elif legacy:
        if not legacy.startswith("eyJ"):
            raise ValueError("Legacy service role key must be a JWT")
        headers.update(apikey=legacy, Authorization=f"Bearer {legacy}")
    else:
        raise ValueError("Configure a server-only Supabase secret key")
    row = prediction_row(prediction)
    transport = session or requests.Session()
    try:
        response = transport.post(url + "/rest/v1/ml_predictions", params={"on_conflict": "id"},
                                  headers=headers, json=row, timeout=(5, 30), allow_redirects=False)
        if not 200 <= response.status_code < 300:
            # Do not include response bodies/headers: they may echo credentials or private data.
            raise RuntimeError(f"Supabase export failed (HTTP {response.status_code})")
    except requests.RequestException:
        raise RuntimeError("Supabase export failed due to a network error") from None
    return {"id": prediction["id"], "uploaded": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prediction", required=True, type=Path)
    parser.add_argument("--upload", action="store_true", help="Actually contact the configured Supabase project")
    args = parser.parse_args()
    prediction = json.loads(args.prediction.read_text(encoding="utf-8"))
    if not args.upload:
        row = prediction_row(prediction)
        print(json.dumps({"dry_run": True, "id": row["id"], "table": "ml_predictions"}))
        return
    print(json.dumps(upload_prediction(prediction)))


if __name__ == "__main__":
    main()
