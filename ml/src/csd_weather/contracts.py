"""Versioned request boundaries for the private numerical inference service."""
from __future__ import annotations

from datetime import date, datetime
import json
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator


class PredictionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    crop: str
    growing_system: str
    sowing_date: date
    prediction_date: date
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    history: dict[str, Any]
    # This service trusts its authenticated backend caller, not browser-supplied ownership.
    farmer_id: int | None = Field(default=None, gt=0, le=9223372036854775807)
    planted_at: datetime | None = None

    @model_validator(mode="after")
    def valid_context(self):
        # Any-shaped provider payloads still must be finite, standards-compliant JSON.
        json.dumps(self.history, allow_nan=False)
        if self.sowing_date > self.prediction_date:
            raise ValueError("Sowing date must not follow prediction date")
        if self.planted_at is not None and self.planted_at.utcoffset() is None:
            raise ValueError("planted_at must include a timezone offset")
        return self
