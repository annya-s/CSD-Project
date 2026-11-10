# Simple offline biomass ML

**Current live crop pages:** use `average_prediction.py` and the 10 crop-specific
synthetic models. See [SYNTHETIC_CROPS.md](SYNTHETIC_CROPS.md) for training, data,
inputs and research/assumptions. Java integration is implemented through
`MLPredictionService` and `MLPredictionClient`. The older instructions below describe
the separate observed winter-wheat pipeline, not the live synthetic comparison.

Three main Python files:
- data_preprocessing.py: read local data or explicitly read Supabase; compute five features.
- train.py: train CatBoost and an age-only baseline, select by validation MAE, save model and metadata.
- predict.py: load the trusted saved model and predict from a JSON request.

Target: experimental winter-wheat dry aboveground biomass in g/m2. Not health, yield or water stress. Other crops/regions are not validated.

## Run from the repository root (Command Prompt)

```cmd
ml\.venv\Scripts\python.exe ml\data_preprocessing.py
ml\.venv\Scripts\python.exe ml\train.py
ml\.venv\Scripts\python.exe ml\predict.py --input ml\data\example_request.json
ml\.venv\Scripts\python.exe -m pytest ml\tests --basetemp=ml/.test-tmp/simple -q
```

Fresh environment: Python 3.12+, then install ml/requirements.txt. Existing .venv works. No editable package installation is needed. The old csd_weather package and FastAPI service are removed: the previous curl /predict commands no longer apply. Stop any old server process. Java can later invoke predict.py and parse its JSON stdout; Java integration is not implemented here.

## Five inputs

Previous 14 complete local days, excluding prediction day:
1. days_since_sowing
2. temperature_min_mean_14d: mean of daily minima of hourly temperatures, degrees C
3. temperature_max_mean_14d: mean of daily maxima of hourly temperatures, degrees C
4. soil_moisture_mean_14d: mean of daily mean 0-7 cm soil moisture, m3/m3
5. solar_radiation_total_14d: sum of daily shortwave radiation, MJ/m2

No VPD, RH, sunshine hours, daylight or 7-day summaries are required. Radiation is not sunshine duration. Historical daily min/max were null in this source period; temperatures were aggregated from the original saved hourly observations. Live collection must use the same hourly-to-local-day extrema, including DST day lengths. Soil and radiation were refreshed once from ECMWF IFS archive weather; normal scripts never fetch weather or datasets.

## Input JSON

The local data/example_request.json is a complete example, using observed study weather. Fields:
- crop: winter_wheat; growing_system: outdoor_soil
- profile: ifs-daily-soil-0-7-v2
- sowing_date and prediction_date: YYYY-MM-DD
- latitude, longitude, timezone (IANA, e.g. Europe/Sofia)
- weather: 14 ordered rows, each with date, temperature_min_c, temperature_max_c, soil_moisture_m3_m3, solar_radiation_mj_m2

Missing/invalid input gives INVALID_INPUT. Missing model gives UNAVAILABLE. Outside training geography or ranges gives UNSUPPORTED. Valid supported input gives EXPERIMENTAL biomass, no confidence percentage. Joblib models must only come from trusted local training.

## Database read (prepared, not connected)

The reader expects a proposed Supabase table/view `ml_daily_weather`, NOT the existing latest-snapshot table. Required columns: crop_cycle_id, profile, date, temperature_min_c, temperature_max_c, soil_moisture_m3_m3, solar_radiation_mj_m2. Unique key: (crop_cycle_id, profile, date). The profile fixes source ECMWF IFS, depth 0-7 cm and the units above. The database/API teammates must implement this contract, retain timezone/location provenance, and restrict access to server credentials. No SQL has been applied.

Set SUPABASE_URL and SUPABASE_SECRET_KEY in the server environment (not frontend; .env files are not auto-loaded). A trusted backend must verify the farmer owns the cycle before invoking this code. Supply planting context JSON with crop_cycle_id and the other input fields; omit weather:

```cmd
ml\.venv\Scripts\python.exe ml\data_preprocessing.py --request context.json --database --output ml\data\prepared_request.json
ml\.venv\Scripts\python.exe ml\predict.py --input ml\data\prepared_request.json
```

Read only: predictions print to stdout, optionally --output result.json. This workflow does not save predictions to Supabase automatically.

## Data, evaluation and deployment

Training is offline. data/training.csv contains 178 observed rows with frozen partitions: 88 train, 32 validation, 58 test. It is rebuilt from existing observations, daily_weather.json and the frozen split. No fabricated biomass labels are used. The old test season has already been inspected, so the new comparison is not a fresh blind evaluation; nearby fields also share weather.

MAE g/m2: CatBoost validation49.93/test71.29; age-only validation183.97/test58.04. CatBoost selected using validation only; weather superiority is NOT established on test. Metrics and training support are saved in model/metadata.json.

Deploy data_preprocessing.py, predict.py, model/model.joblib, model/metadata.json and runtime dependencies. Do not deploy train.py, training data, tests, backups or the local .venv. requirements.txt includes pytest for development; omit pytest in a production dependency installation.

Data, models, backups and outputs are ignored by Git. Existing data files have been removed from the index, not from disk; this does not erase previous Git history. Share training data separately with teammates, with source/license documentation. A fresh clone needs the static data bundle to retrain and the model artifact bundle to predict. Tests currently use the local data/model bundle. SOURCE_DATA.md records sources. Do not commit credential files.

Original datasets are preserved under data/. Previous code and artifacts are recoverable from archive/previous-ml.zip; they are not part of the active implementation.
