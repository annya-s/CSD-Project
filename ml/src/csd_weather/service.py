"""Private inference API. Bind to loopback unless an internal token is configured."""
from __future__ import annotations

import argparse
import hmac
import os
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Request

from .contracts import PredictionRequest
from .predict import Predictor, save_prediction


def create_app(artifact_dir: str | Path | None = None, prediction_dir: str | Path | None = None,
               api_token: str | None = None) -> FastAPI:
    app = FastAPI(title="Experimental wheat biomass", version="0.1.0", docs_url=None, redoc_url=None, openapi_url=None)
    predictor = Predictor(artifact_dir)
    token = api_token if api_token is not None else os.getenv("ML_API_TOKEN", "")

    def authorized(request: Request):
        if token:
            supplied = request.headers.get("authorization", "")
            if not hmac.compare_digest(supplied, f"Bearer {token}"):
                raise HTTPException(status_code=401, detail="Internal authentication required")
        elif request.client is None or request.client.host not in {"127.0.0.1", "::1"}:
            raise HTTPException(status_code=403, detail="Unconfigured service accepts loopback only")

    @app.get("/ready", dependencies=[Depends(authorized)])
    def ready():
        if not predictor.ready:
            raise HTTPException(status_code=503, detail="Model artifacts unavailable")
        return {"ready": True, "model_version": predictor.selection["model_version"], "validation_status": "EXPERIMENTAL"}

    @app.post("/predict", dependencies=[Depends(authorized)])
    def predict(body: PredictionRequest):
        result = predictor.predict(body)
        save_prediction(result, prediction_dir)
        return result

    return app


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8001)
    args = parser.parse_args()
    if args.host not in {"127.0.0.1", "::1", "localhost"} and not os.getenv("ML_API_TOKEN"):
        parser.error("Set ML_API_TOKEN before binding beyond loopback")
    import uvicorn
    uvicorn.run(create_app(), host=args.host, port=args.port, proxy_headers=False)


if __name__ == "__main__":
    main()
