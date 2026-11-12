# Synthetic full-growth biomass models

Updated 11 November 2026. This replaces the earlier age-plus-14-day comparison.

The last 14 complete days are the **weather input window**, not the prediction horizon.
The product compares two scenarios at the same assumed full-growth date (saved planting
 date plus the profile's maturity_days): continued recent weather and assumed optimal
conditions. Neither is a measured current biomass estimate or validated harvest forecast.

The target is the profile's mature_target. It does not change with current planting age.
The model predicts a fraction of that target, explicitly bounded to [0, 1], then converts
it to the crop's biomass unit. This bound is a simulation assumption, not evidence that
real plants cannot exceed these reference numbers. Apple uses a 23-year woody biomass
reference, not a universally established maturity age or apple fruit yield.

## Training and input contract

Run `ml/.venv/bin/python ml/average_prediction.py` from the project root in WSL.
Windows: `ml\.venv\Scripts\python.exe ml\average_prediction.py`.
No network or database is needed for offline training.

The static dataset is `ml/data/synthetic_averages.csv`, 1,000 rows per crop,
10,000 total, with SYNTHETIC origin and 800/200 train/test split per crop.
Saved models: `ml/model/averages/`. Metadata profile: `mature-fraction-v3`.
Version 2 models must be rebuilt; they predicted absolute biomass, not fractions.
Package all ten model files and their metadata for deployment; do not package a venv.

Features are age in days, mean temperature C, volumetric soil moisture m3/m3,
VPD kPa and mean daily sunshine hours. Java supplies sunshine seconds, converted once.
Training bounds: temperature -50 to 50 C, moisture 0 to 0.6, VPD 0 to 5, sunshine 0 to 24.
Extreme cold scenarios now return very low values instead of the old generic error.
Invalid or missing readings are still rejected, not replaced with invented weather.

Seed 42. 200 rows have reference weather; 300 additional rows use the full-growth age.
The synthetic fraction label is interpolated age biomass / mature_target multiplied by:
`clip(1 - .025*abs(T-Tref) - 2*abs(M-Mref) - .12*abs(V-Vref) - .025*abs(S-Sref), 0, 1)`.
All response coefficients and moisture/VPD/sunshine references are invented assumptions.
At serving time the model is evaluated at the full-growth age, using recent weather.
Current planting age determines the projected date and checks that the crop is not
already past the chosen full-growth stage. The scenario ignores earlier weather,
management, senescence and seasonal changes; it cannot estimate recoverable losses.
Holdout MAE measures imitation of this recipe in fraction units, not field accuracy.

## Full-growth reference profiles

| Crop | Assumed age (days) | Target | Unit |
|---|---:|---:|---|
| CARROT | 130 | 4.413 | g/plant dry shoot |
| MAIZE | 150 | 10000.000 | g/m2 fresh aboveground |
| SOYBEAN | 135 | 5000.000 | g/m2 fresh aboveground |
| WHEAT | 263 | 1371.000 | g/m2 dry aboveground |
| POTATO | 120 | 300.000 | g/m2 dry aboveground |
| SUGAR_CANE | 365 | 3000.000 | g/m2 dry aboveground |
| RICE | 150 | 1200.000 | g/m2 dry aboveground |
| TOMATO | 150 | 600.000 | g/m2 dry aboveground |
| LETTUCE | 75 | 200.000 | g/m2 dry aboveground |
| APPLE | 8395 | 24.800 | kg/tree dry aboveground wood |

Carrot retains the observed dry-leaf median anchor; wheat uses the maximum of the
existing observed age medians as a synthetic full-growth reference. Wheat's intermediate
curve is now assumed at 5%, 35%, 85%, 100% of that target at 20%, 50%, 80%, 100% of
its 263-day growing period, using the same curve recipe as the other assumed annual
profiles. It has 1,000 synthetic rows with the same columns, generator and training
split as the other nine crops. The historical wheat sample endpoint and button are
removed; the original offline research files remain archived from the live flow. Maize and soybean
retain measured early anchors but now extend to invented full-growth endpoints of
10,000 g/m2 fresh at 150 days and 5,000 g/m2 fresh at 135 days respectively.
These two extensions are design assumptions, not measurements from MuST-C.
The remaining annual profiles are assumed curves informed by source context, not
extracted field labels. Apple retains the published 23-year dry wood endpoint with
invented earlier values. Details and source IDs remain in crop_profiles.json.
Fresh biomass, dry biomass, shoot biomass, root yield and fruit yield are distinct.

Targets can be shown without weather or model artifacts, but Python dependencies
are still required. Runtime tests: `python -m unittest discover -s ml/tests -p test_average_prediction.py`.

## Research references (source IDs used in JSON)


- FAO_DURATION / FAO_WATER: [FAO crop water needs](https://www.fao.org/4/s2022e/s2022e02.htm)
  and [growth stages](https://www.fao.org/4/s2022e/s2022e07.htm): duration and
  crop water-demand context; do not supply our biomass or moisture labels.
- FAO_POTATO: [FAO potato production](https://www.fao.org/4/i0500e/i0500e02.pdf):
  approximately 120-day production example and tuber yields. Our shoot scale is an assumption.
- FAO_SUGARCANE: [Crop yield response to water](https://www.fao.org/4/i2800e/i2800e.pdf):
  commercial fresh cane yields provide scale context, not a measured dry shoot conversion.
- TOMATO_TEMP: [Virginia Tech tomato physiology](https://www.pubs.ext.vt.edu/SPES/spes-508/spes-508.html):
  temperature guidance; no measured labels imported.
- LETTUCE: [Wageningen lettuce light-use efficiency review](https://research.wur.nl/en/publications/light-use-efficiency-of-lettuce-cultivation-in-vertical-farms-com/):
  distinguishes shoot dry weight, growing environment, density and cultivation period.
  Our 200 g/m2 endpoint is a scenario assumption, not a value taken from this review.
- FABA: [Tsige et al., 2022](https://doi.org/10.1155/2022/2232961): measured aboveground
  dry biomass on the order of several t/ha; supports the scale, not our weather response.
- TR32: [Reichenau et al., 2020](https://essd.copernicus.org/articles/12/2333/2020/):
  research dataset covering wheat, barley, spelt, rapeseed and other field crops.
  New profiles are not fitted to its raw measurements; cereal and rapeseed endpoints remain assumptions.
- APPLE: [Irish Apple Growers Association report](https://cdn.npf.ie/wp-content/uploads/0331-Con-Traas-Irish-Apple-Growers-Association.compressed.pdf):
  24.8 kg dry aboveground mass for sampled 23-year trees; not transferable to every orchard.
- CARROT: [Turner carrot diallel data](https://github.com/mishaploid/carrot-diallel),
  commit `9c45b01737b4c4e14cc8c8373591defffae065fb`, `dlw` median; see SOURCE_DATA.md.
- MUSTC: [University of Bonn MuST-C](https://www.ipb.uni-bonn.de/data/MuST-C/),
  metadata biomass table, excluding `Dont_use_psqm == 1`; see SOURCE_DATA.md.
- EXISTING_WHEAT: existing `data/processed/observations.csv`; historical source and
  processing are documented in SOURCE_DATA.md. The separate observed wheat model is preserved.


## Soil-depth alignment

Current and historical collection now both request soil_moisture_3_to_9cm.
Synthetic input moisture represents volumetric water content at 3–9 cm. Generated
CSV rows and model metadata record this depth. Numerical ranges and penalties remain
assumptions: this is not a conversion or calibration of measured 0–7 cm data.
Each planting refreshes once after application restart to replace old untagged averages,
then reuses complete current rows. Missing values remain missing rather than being filled.
