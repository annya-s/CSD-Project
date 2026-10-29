# Winter-wheat biomass experiment

The target is measured dry aboveground biomass (g/m²), not crop health.

Selection uses grouped first-season validation only. The second season is an untouched temporal evaluation set; it never tunes or selects the model.

Selected candidate: **age_only**. Status: **EXPERIMENTAL**.

| Candidate | Validation MAE | Holdout MAE | Holdout median absolute error | Holdout bias |
|---|---:|---:|---:|---:|
| median | 30.67 | 114.00 | 113.25 | 113.25 |
| age_only | 9.30 | 48.60 | 48.26 | 47.30 |
| catboost | 24.78 | 109.25 | 108.58 | 108.58 |

Weather improvement gate on holdout: **FAIL**.

This gate concerns promotion of the weather claim; it does not retrospectively change the validation-selected candidate.

## Partition counts

| Partition | Observations | Field-seasons | Sampling dates | Provider grid/date groups |
|---|---:|---:|---:|---:|
| train | 12 | 4 | 3 | None |
| validation | 6 | 2 | 3 | None |
| test | 12 | 6 | 2 | None |

## Limits

- One Bulgarian winter-wheat region; this is not validation across regions or varieties.
- Adjacent fields can share weather-grid cells, dates and management; rows are not independent farms.
- The temporal holdout covers November and April only, not late-season performance.
- Biomass is not a health percentage; unobserved soil, pests and management affect growth.
- Historical modelled weather is not a field sensor or proof of issue-time availability.
- No calibrated confidence percentage or prediction interval is available.
- Weather did not improve holdout MAE over planting age alone; do not claim weather adds predictive value.
- Some temporal holdout observations are outside the training planting-age range; primary errors retain them and age-supported subgroup errors are reported separately.

## Age support in the temporal holdout

Training ages: 170–231 days. Held-out rows inside that range: 6; outside: 6.

All held-out rows remain in the primary table. Age-supported subgroup errors are also saved in evaluation.json; they do not replace the full test results.

| Candidate | Within-age-range n | Within-age-range MAE |
|---|---:|---:|
| median | 6 | 1.50 |
| age_only | 6 | 1.65 |
| catboost | 6 | 1.50 |

No confidence percentage or prediction interval is estimated. Reanalysis evaluation is retrospective, not evidence of operational availability at past issue times.

See evaluation.json for age/season errors, candidate configuration, data hashes, and selection evidence.
