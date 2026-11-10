# Static data provenance

Original measured wheat biomass: https://zenodo.org/records/17475742 (CC BY 4.0; attribution and original metadata retained in the local data/manifests directory). Original source TXT files remain unchanged locally. Labels are dry aboveground biomass, not health.

Weather: https://open-meteo.com/en/docs/historical-weather-api with model ecmwf_ifs. New raw responses and request parameters/checksums are in local data/raw/daily_weather_v2 and data/manifests/daily_weather.json. Daily temperatures derive from the prior saved hourly temperature_2m observations because requested daily min/max returned null. Radiation is genuine shortwave_radiation_sum, not converted sunshine. Daily soil means use all local-day hourly 0-7 cm values. Preserve this definition at inference.

Data collected/prepared 2026-10-29. Data and trained model are distributed separately from Git; archive provenance, licenses, split manifest and checksums with any shared bundle. Do not replace measured labels with synthetic values.

## Additional crop downloads (2026-10-30)

These are candidate source datasets, NOT incorporated into training.csv or the deployed wheat model. No new crop model was trained. Download manifests retain URLs, timestamps, sizes and SHA-256 hashes. Original source files are unchanged.

### Rice: Binyang field experiments, China

- Source: https://huggingface.co/datasets/qiyang577/BinyangExperiments
- Authors: Q. Yang, J. Han, Z. Chen, J. Yu, Y. Zha and L. Shi; attribution details in the downloaded README.
- License: CC BY-SA 4.0; retain attribution and share-alike terms for adapted data.
- Local directory: `data/raw/rice/binyang/`.
- Downloaded: README, GapFilledWeather_2017-2019.csv, Crop_trait_measurement_2018.zip and Crop_trait_measurement_2019.zip. Archives are also extracted under `extracted/` with their original directory structure.
- Four Biomass CSVs contain 159 plot-season rows and 850 nonempty measurement cells across dates. These are repeated measurements, not 850 independent plots. Units are g/m2 aboveground biomass according to the source README; confirm dry-matter methodology before equating them with the wheat target.
- Weather includes daily Tmin, Tmax, radiation, rainfall, wind and humidity. Soil moisture is absent. Confirm plot planting dates and measurement dates (campaigns span 2-3 days); phenology is not a substitute for planting dates.
- Paddy rice differs from upland soil crops: shallow modelled soil moisture may not capture flooding/irrigation. Obtain compatible weather and management information before training. Preserve plot/year grouping for evaluation.

### Maize: Ghana field observations

- Source: https://zenodo.org/records/6632083 (CC BY 4.0; full author metadata saved).
- Local directory: `data/raw/maize/zenodo_6632083/`.
- Downloaded: Yield_Maize_Biomass_V2.csv, Ghana_ground_data_v5.csv, and field-point/field-polygon GeoJSON files; source_metadata.json and download_manifest.json retained.
- Yield/biomass table has 144 rows, with substantial missing biomass values. Grain yield, wet weight, dry weight and total biomass are separate columns; do not treat every row as a biomass observation or duplicate field averages across quadrants as independent labels.
- Before use: confirm biomass definition, units, sampled area, harvest/planting dates and field joins in the source methods; assemble matching 14-day weather. This is primarily harvest biomass, not a complete within-season growth series.

### Maize: Texas field measurements

- Source: https://zenodo.org/records/19176737 (CC BY 4.0; full author metadata saved).
- Local directory: `data/raw/maize/zenodo_19176737/`.
- Downloaded: All_variables.xlsx (256 data rows), source metadata and manifest. Published checksums verified for both maize downloads.
- Columns include year, growth stage, treatment, replicate, spectral bands/indices, biomass (Mg/ha), height, LAI, SPAD, nitrogen uptake and grain yield. Mg/ha converts to g/m2 by multiplying by 100, but dry/fresh definition must still be checked.
- Exact observation dates, planting dates and weather are not columns in this workbook. Stage/year cannot establish a 14-day weather window. UAV predictors are not available from the application's weather API, so this is a candidate label source, not a drop-in weather training table.

### Sources checked but not downloaded

- Potato: https://doi.org/10.7910/DVN/1LC6W7; field biomass/weather/soil-moisture dataset described at https://research.wur.nl/en/datasets/agronomic-dataset-on-potato-growth-and-yield-in-the-netherlands/ . Public metadata API returned HTTP 403 here.
- Lettuce: https://doi.org/10.5061/dryad.73n5tb3d4; metadata accessible, but file download returned HTTP 401 via API and HTTP 403 via the public download link. No lettuce measurements were saved. Its Weight label is fresh harvest mass in g/plant, not the current dry biomass g/m2 target; imagery-derived predictors also differ from the weather-only application.

Next preparation step: select a crop with verified labels and dates, normalize its units, join compatible weather, and split by field/season before adapting the three Python scripts for that crop. Never fill missing observed biomass or planting dates with invented values. Simply copying these files into data/raw does not make the current wheat pipeline multicrop.

## Synthetic biomass scenarios
Run `synthetic_demo.py` offline to rebuild 1,500 seeded synthetic examples per crop in data/synthetic_biomass.csv and four CatBoost artifacts in model/synthetic/. Ship those artifacts, their age-range JSON files and synthetic_demo.py with the existing ML runtime. Data and models are ignored by Git. No simulation is used as a wheat training label or saved as a farmer observation.

Carrot: Turner et al., https://github.com/mishaploid/carrot-diallel/blob/9c45b01737b4c4e14cc8c8373591defffae065fb/data/diallelRawData.csv . Use the median raw dry leaf weight (dlw), grams per plant, at the study's 130-day harvest. Day 14 and 65 anchors at 3% and 31% of that median are invented. Source study: https://academic.oup.com/g3journal/article/8/2/411/6029812.

Maize, soybean and sugar beet: Chong et al., MuST-C, CC BY 4.0, https://www.ipb.uni-bonn.de/data/MuST-C/ and https://doi.org/10.1038/s41597-025-06462-y . Source archive: https://bonndata.uni-bonn.de/api/access/datafile/:persistentId?persistentId=doi:10.60507/FK2/OX9XTM/CEFQFD . Use table 1 sowing dates, omit individual-plant records flagged Dont_use_psqm, and interpolate median fresh aboveground biomass per full plot area between observed dates. This fresh weight includes water and cannot be directly compared with wheat dry mass or carrot dry shoot mass per plant.

The generator samples ranges of 14-day weather summaries, applies explicitly assumed weather penalties to each growth curve, and fits each model to those simulated values. These scenarios demonstrate the assumed relationship; they have no field accuracy estimate. No synthetic record is treated as independent field validation.


## Stored-average comparison (2026-11-10)
average_prediction.py trains offline and writes model/averages/*.cbm and *.json.
Run: python average_prediction.py. It uses the existing carrot/Bonn curves from
synthetic_demo.py and observed wheat age/biomass medians from
data/processed/observations.csv. Every training label in this comparison model
is synthetic, including wheat. The historical observed-data wheat example
continues to use its original saved model.

The live Java reader uses public.CROP_WEATHER_HISTORICAL_AVERAGE by authenticated
crop cycle ID. Required values are Start_Date, End_Date, Temperature_Mean,
Soil_Moisture_Mean, Vapour_Pressure_Deficit_Mean, Sunshine_Duration_Mean_Seconds.
Its date window must end yesterday and begin 14 days ago. It uses the server
timezone because EnvironmentClient uses that timezone. Date_Planted determines age.
Missing/stale averages cause INSUFFICIENT_DATA. This reader never fetches weather.
The API producer must call storeAverageHistoricalReading for each planting and
ensure complete source data. Its current calculation skips nulls, and the table
has no sample counts, so date validation alone cannot prove completeness.

The model compares now, age+14 under constant recent averages, and age+14 under
assumed reference means (20 C, 0.26 m3/m3 soil water, 1 kPa VPD, 8 sunshine hours/day).
Reference assumptions are shared demonstration settings, not validated crop optima.
The generator's penalties are explicitly synthetic. No growth beyond the observed
age endpoints is extrapolated. One recent average window cannot establish a real
crop's accumulated biomass or causally predict management benefits.
The metadata's synthetic_holdout_mae measures imitation of the generator only.
It is not field accuracy or a confidence interval. Deploy model/averages plus both
average_prediction.py and synthetic_demo.py and the installed Python dependencies.
