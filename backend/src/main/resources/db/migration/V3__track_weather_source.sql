-- [LEGACY UUID SCHEMA] Old migration for farm.crop_entry. Kept as reference; do not apply to the public ERD schema.
-- Existing snapshots came from the manual/test endpoint, not from a verified API fetch.
ALTER TABLE farm.crop_entry ADD COLUMN weather_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
    CHECK (weather_source IN ('MANUAL', 'OPEN_METEO'));
