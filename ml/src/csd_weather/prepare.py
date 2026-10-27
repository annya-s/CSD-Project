"""Parse real trial tables into dated biomass observations, with explicit rejections."""
from __future__ import annotations

import argparse
import re
import shutil
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
from pyproj import Transformer

from .common import ML_ROOT, config, sha256, write_json

SOURCE_FILES = ["ESU_Biophysical_and_biochemical_crop_data.txt", "ESU_Biophysical_and_biochemical_crop_data_info.txt",
                "Crop_calendar_and_management.txt", "Crop_calendar_and_management_info.txt"]


def import_local_sources(root: Path) -> Path:
    """Preserve supplied originals and hash local copies without any metadata request."""
    raw = root / "data/raw"
    canonical = raw / "zenodo_17475742"
    canonical.mkdir(parents=True, exist_ok=True)
    files = []
    for name in SOURCE_FILES:
        source, destination = raw / name, canonical / name
        if not destination.exists():
            if not source.is_file():
                raise FileNotFoundError(f"Required static source is missing: {source}")
            shutil.copyfile(source, destination)
        if source.exists() and sha256(source) != sha256(destination):
            raise ValueError(f"Original and canonical source differ: {name}; inspect without overwriting")
        files.append({"name": name, "path": str(destination.relative_to(root)), "sha256": sha256(destination)})
    write_json(root / "data/manifests/local_sources.json", {
        "source_mode": "STATIC_LOCAL_FILES_ONLY", "files": files,
        "record_url": "https://zenodo.org/records/17475742",
        "verification": "Local content hashes; original downloaded provenance remains in sources.json when present"})
    return canonical


def read_table(path: str | Path) -> pd.DataFrame:
    # pandas' CSV parser preserves quoted multiline cells in the calendar TSV.
    frame = pd.read_csv(path, sep="\t", na_values=["NA"], encoding="utf-8-sig")
    original_columns = frame.columns.tolist()
    frame.columns = frame.columns.str.strip()
    if frame.columns.duplicated().any():
        raise ValueError("Duplicate normalized source columns")
    frame.attrs["source_column_mapping"] = dict(zip(original_columns, frame.columns))
    return frame


def iso_date(value) -> date:
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise ValueError("MISSING_OR_INVALID_DATE")
    return date.fromisoformat(value)


def prepare_tables(biomass: pd.DataFrame, calendar: pd.DataFrame, settings: dict | None = None) -> tuple[pd.DataFrame, list]:
    settings = settings or config()
    if biomass.ESU_ID.duplicated().any():
        raise ValueError("Duplicate source visit ID")
    if calendar.Unit_ID.duplicated().any():
        raise ValueError("Calendar Unit_ID must be unique")
    calendar = calendar.set_index("Unit_ID")
    transformer = Transformer.from_crs(settings["source_crs"], settings["output_crs"], always_xy=True)
    rows, rejected = [], []
    for record in biomass.to_dict("records"):
        visit = record["ESU_ID"]
        try:
            target_date = iso_date(record["AGB_N_date"])
            season = settings["campaign_seasons"].get(record["Campaign"])
            if not season:
                raise ValueError("UNKNOWN_CAMPAIGN")
            # Source uses both three-letter prefixes and the four-letter "june".
            match = re.fullmatch(r"[a-z]{3,4}\d{4}_(\d+)_(\d+)", visit)
            if not match:
                raise ValueError("INVALID_VISIT_ID")
            field = f"{season}_U{match.group(1)}"
            if field not in calendar.index:
                raise ValueError("MISSING_CALENDAR_MATCH")
            sowing = iso_date(calendar.loc[field, "Sowing_date"])
            age = (target_date - sowing).days
            if age <= 0:
                raise ValueError("INVALID_PLANTING_AGE")
            target = float(record["AGBd_wheat"])
            if not np.isfinite(target) or target < 0:
                raise ValueError("INVALID_BIOMASS")
            longitude, latitude = transformer.transform(float(record["Point_X_UTM34"]), float(record["Point_Y_UTM34"]))
            if not np.isfinite([latitude, longitude]).all() or not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                raise ValueError("INVALID_COORDINATES")
            rows.append({"visit_id": visit, "field_season_id": field, "season": season,
                         "target_date": target_date.isoformat(), "sowing_date": sowing.isoformat(),
                         "latitude": latitude, "longitude": longitude, "days_since_sowing": age,
                         "target_dry_biomass_g_m2": target, "data_origin": "OBSERVED"})
        except (ValueError, TypeError) as error:
            rejected.append({"visit_id": str(visit), "reason": str(error)})
    if not rows:
        raise ValueError("No valid dated observations")
    return pd.DataFrame(rows).sort_values("visit_id").reset_index(drop=True), rejected


def prepare(root: Path = ML_ROOT) -> pd.DataFrame:
    raw = import_local_sources(root)
    biomass_path = raw / "ESU_Biophysical_and_biochemical_crop_data.txt"
    calendar_path = raw / "Crop_calendar_and_management.txt"
    biomass, calendar = read_table(biomass_path), read_table(calendar_path)
    observations, rejected = prepare_tables(biomass, calendar)
    processed = root / "data/processed"
    processed.mkdir(parents=True, exist_ok=True)
    observations.to_csv(processed / "observations.csv", index=False)
    write_json(processed / "rejections.json", rejected)
    audit = {"raw_rows": len(biomass), "dated_observations": len(observations), "rejected_rows": len(rejected),
             "field_seasons": int(observations.field_season_id.nunique()),
             "by_season": observations.groupby("season").size().to_dict(),
             "biomass_sha256": sha256(biomass_path), "calendar_sha256": sha256(calendar_path),
             "target_column": "AGBd_wheat", "date_column": "AGB_N_date", "target_units": "g/m2",
             "source_crs": "EPSG:32634", "output_crs": "EPSG:4326",
             "source_column_mappings": {"biomass": biomass.attrs["source_column_mapping"],
                                        "calendar": calendar.attrs["source_column_mapping"]}}
    write_json(processed / "preparation_audit.json", audit)
    return observations


def main() -> None:
    argparse.ArgumentParser(description=__doc__).parse_args()
    frame = prepare()
    print(f"Prepared {len(frame)} dated observations across {frame.field_season_id.nunique()} field-seasons.")


if __name__ == "__main__":
    main()
