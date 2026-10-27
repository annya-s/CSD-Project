# Winter-wheat biomass experiment

The target is measured dry aboveground biomass (g/m²), not crop health.

Selection uses grouped first-season validation only. The second season is an untouched temporal evaluation set; it never tunes or selects the model.

Selected candidate: **catboost**. Status: **EXPERIMENTAL**.

| Candidate | Validation MAE | Holdout MAE | Holdout median absolute error | Holdout bias |
|---|---:|---:|---:|---:|
| median | 495.28 | 318.03 | 371.50 | 315.83 |
| age_only | 183.97 | 58.04 | 35.22 | 2.45 |
| catboost | 74.18 | 123.61 | 140.82 | 47.17 |

Weather improvement gate on holdout: **FAIL**.

This gate concerns promotion of the weather claim; it does not retrospectively change the validation-selected candidate.

## Partition counts

| Partition | Observations | Field-seasons | Sampling dates | Provider grid/date groups |
|---|---:|---:|---:|---:|
| train | 88 | 4 | 13 | 25 |
| validation | 32 | 2 | 8 | 8 |
| test | 58 | 6 | 6 | 9 |

Static dataset: 6 provider grid cells and 38 distinct grid/date groups. Train and validation share 4 grid/date groups. Request-window IDs are location-specific cache keys, not independent weather samples. The frozen split was retained; this dependence weakens validation evidence.

## Limits

- One Bulgarian winter-wheat region; this is not validation across regions or varieties.
- Adjacent fields can share weather-grid cells, dates and management; rows are not independent farms.
- The temporal holdout covers November and April only, not late-season performance.
- Biomass is not a health percentage; unobserved soil, pests and management affect growth.
- Historical modelled weather is not a field sensor or proof of issue-time availability.
- No calibrated confidence percentage or prediction interval is available.
- Weather did not improve holdout MAE over planting age alone; do not claim weather adds predictive value.
- Some temporal holdout observations are outside the training planting-age range; primary errors retain them and age-supported subgroup errors are reported separately.
- Train and validation share 4 provider-grid/date groups; grouped field splitting does not ensure independent weather. Frozen splits were not changed after evaluation.

## Age support in the temporal holdout

Training ages: 148–263 days. Held-out rows inside that range: 30; outside: 28.

All held-out rows remain in the primary table. Age-supported subgroup errors are also saved in evaluation.json; they do not replace the full test results.

| Candidate | Within-age-range n | Within-age-range MAE |
|---|---:|---:|
| median | 30 | 222.03 |
| age_only | 30 | 81.31 |
| catboost | 30 | 87.45 |

No confidence percentage or prediction interval is estimated. Reanalysis evaluation is retrospective, not evidence of operational availability at past issue times.

See evaluation.json for age/season errors, candidate configuration, data hashes, and selection evidence.
