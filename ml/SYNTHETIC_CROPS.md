# Synthetic crop demonstrations

Updated 10 November 2026. Covers the 10 crops listed in Java's CropConditions map.
The database can contain additional names; those are not automatically supported by Java or ML.

## Run offline

From the project root in WSL, with dependencies installed in your Linux venv:

```sh
ml/.venv/bin/python ml/average_prediction.py
ml/.venv/bin/python -m unittest discover -s ml/tests -p test_average_prediction.py
```

Windows equivalent: `ml\.venv\Scripts\python.exe ml\average_prediction.py`.
Training requires no database or downloads. The checked-in `crop_profiles.json` contains
the compact anchor data and recipe assumptions. The generated static dataset is
`data/synthetic_averages.csv`: 1,000 rows per crop, 10,000 total, with crop, units,
SYNTHETIC origin and an explicit 800/200 train/test split per crop.
Models and metadata are in `model/averages/`. These generated artifacts remain
gitignored; package that directory with the deployed Python code. Do not deploy a venv.

## Meaning and limitations

Inputs: planting age in days, previous 14 complete days' mean temperature (C),
mean volumetric soil moisture (m3/m3), mean VPD (kPa), and mean daily sunshine (hours).
Java sends sunshine seconds; Python converts once. Java supplies the saved planting
date and averages, and Python derives age. The historical weather producer uses
0–7 cm moisture. These synthetic labels do not validate that depth as root-zone moisture.

Each crop has its own CatBoost model. Biomass is interpolated between its age anchors
and multiplied by an invented weather suitability factor:
`clip(1 - .025*abs(T-Tref) - 2*abs(M-Mref) - .12*abs(V-Vref) - .025*abs(S-Sref), .05, 1)`.
References are in the JSON, ordered temperature, moisture, VPD, sunshine.
All moisture/VPD/sunshine reference values and response coefficients are teaching
assumptions, not published crop-specific optima. Soil texture, irrigation, cultivar,
density, fertility, dormancy, pests and past-season weather are not modelled.

Random seed 42 makes generation reproducible. 200 rows per crop use reference
weather; 800 sample the allowed weather bounds. The shuffled holdout only measures
how well CatBoost imitates this recipe. It does not measure real-world accuracy.
Independent random weather draws include rare combinations; they are scenario coverage,
not a simulated regional weather record.

Both displayed comparisons use age + 14 days. They are independent age/weather
scenarios, not a growth simulation initialized from measured current biomass.
They cannot establish recoverable losses or promise that changing weather now recovers
the difference. The reference can occasionally be lower because of approximation error;
the UI preserves the signed difference. Predictions beyond the profile's last age are
unavailable instead of silently extending a crop's season.

## Anchor provenance

| Crop | Target unit | Anchor basis |
|---|---|---|
| Carrot | g/plant dry shoot | Existing dry leaf median 4.4133 at 130 days; earlier values invented |
| Maize | g/m2 fresh aboveground | MuST-C dated plot medians, days 36–84 |
| Soybean | g/m2 fresh aboveground | MuST-C dated plot medians, days 41–97 |
| Winter wheat | g/m2 dry aboveground | Existing saved observations, median by planting age |
| Potato | g/m2 dry aboveground | Assumed 300 at day 120; excludes tubers |
| Sugar cane | g/m2 dry aboveground | Assumed 3,000 at day 365 |
| Rice | g/m2 dry aboveground | Assumed 1,200 at day 150 |
| Tomato | g/m2 dry aboveground | Assumed 600 at day 150; not marketable tomato yield |
| Lettuce | g/m2 dry aboveground | Assumed 200 at day 75 |
| Apple | kg/tree dry aboveground wood | Reported 24.8 kg at 23 years; earlier values invented |

For the five new annual profiles, intermediate anchors are 5%, 35%, 85%, 100% of
the assumed endpoint at 20%, 50%, 80%, 100% of the assumed duration. These curves
omit senescence and harvest. Other than apple's endpoint and the original four
profiles, **the biomass labels are invented, not extracted from the linked studies**.
Published crop yield is not silently relabelled as aboveground biomass. For context,
1 t/ha = 100 g/m2; fresh and dry masses are not interchangeable.

Apple assumes a young transplanted tree at the saved planting date. Logging an
already mature tree today does not establish its age. The demonstration excludes
fruit yield, pruning and winter dormancy. Do not interpret two weeks of weather as
a causal change to a mature tree's accumulated woody biomass.

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

The historical wheat example now appears only on WHEAT pages. It is separate from
the live synthetic comparison. Restart Spring Boot and reload the page after updates.
Live estimates still need complete, current averages in the database; absent or stale
readings correctly show unavailable rather than substituting wheat or invented weather.

The six extra crop profiles and their generated rows/models were removed on request.
Shared raw research archives are retained because they also contain maize and soybean data.
