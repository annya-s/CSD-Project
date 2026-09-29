-- Store the latest complete local day's readings with each planting.
-- Reference ranges remain in CropConditions.java for now.
ALTER TABLE farm.crop_entry ADD COLUMN weather_date DATE;
ALTER TABLE farm.crop_entry ADD COLUMN weather_timezone VARCHAR(100);
ALTER TABLE farm.crop_entry ADD COLUMN temperature_mean_c DOUBLE PRECISION
    CHECK (temperature_mean_c BETWEEN -100 AND 100);
ALTER TABLE farm.crop_entry ADD COLUMN sunshine_duration_seconds DOUBLE PRECISION
    CHECK (sunshine_duration_seconds BETWEEN 0 AND 86400);
ALTER TABLE farm.crop_entry ADD COLUMN humidity_mean_percent DOUBLE PRECISION
    CHECK (humidity_mean_percent BETWEEN 0 AND 100);
ALTER TABLE farm.crop_entry ADD COLUMN soil_moisture_mean_m3m3 DOUBLE PRECISION
    CHECK (soil_moisture_mean_m3m3 BETWEEN 0 AND 1);
