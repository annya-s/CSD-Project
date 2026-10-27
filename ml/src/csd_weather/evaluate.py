"""Observed biomass errors and explicit independence/support limitations."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd

from .splits import TARGET, validate_observed
from .common import ML_ROOT


def regression_metrics(actual, predicted) -> dict:
    actual, predicted = np.asarray(actual, dtype=float), np.asarray(predicted, dtype=float)
    if actual.ndim != 1 or predicted.ndim != 1 or actual.shape != predicted.shape or not len(actual):
        raise ValueError("Nonempty aligned targets/predictions required")
    if not np.isfinite(actual).all() or not np.isfinite(predicted).all():
        raise ValueError("Non-finite evaluation value")
    error = predicted - actual
    return {"n": len(actual), "mae_g_m2": float(np.abs(error).mean()),
            "median_absolute_error_g_m2": float(np.median(np.abs(error))),
            "bias_g_m2": float(error.mean()), "rmse_g_m2": float(np.sqrt(np.mean(error ** 2)))}


def evaluate_predictions(frame: pd.DataFrame, predicted) -> dict:
    validate_observed(frame)
    values = np.asarray(predicted, dtype=float)
    overall = regression_metrics(frame[TARGET], values)
    work = frame.copy()
    work["prediction"] = values
    work["age_band"] = pd.cut(work.days_since_sowing, [-1, 60, 120, 180, 240, np.inf],
                              labels=["0-60", "61-120", "121-180", "181-240", "241+"])
    overall["field_seasons"] = int(work.field_season_id.nunique())
    overall["sampling_dates"] = int(work.target_date.nunique())
    overall["weather_request_windows"] = int(work.weather_window_id.nunique()) if "weather_window_id" in work else None
    if {"weather_grid_latitude", "weather_grid_longitude"}.issubset(work.columns):
        overall["weather_grid_date_groups"] = len(work[["weather_grid_latitude", "weather_grid_longitude", "target_date"]].dropna().drop_duplicates())
    overall["by_season"] = {str(key): regression_metrics(group[TARGET], group.prediction)
                            for key, group in work.groupby("season")}
    overall["by_age_days"] = {str(key): regression_metrics(group[TARGET], group.prediction)
                              for key, group in work.groupby("age_band", observed=True)}
    return overall


def write_report(evaluation: dict, output: str | Path) -> None:
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "evaluation.json").write_text(json.dumps(evaluation, indent=2, allow_nan=False), encoding="utf-8")
    lines = ["# Winter-wheat biomass experiment", "", "The target is measured dry aboveground biomass (g/m²), not crop health.", "",
             "Selection uses grouped first-season validation only. The second season is an untouched temporal evaluation set; it never tunes or selects the model.", "",
             f"Selected candidate: **{evaluation['selected_model']}**. Status: **EXPERIMENTAL**.", "",
             "| Candidate | Validation MAE | Holdout MAE | Holdout median absolute error | Holdout bias |", "|---|---:|---:|---:|---:|"]
    for name, results in evaluation["models"].items():
        val, test = results["validation"], results["test"]
        lines.append(f"| {name} | {val['mae_g_m2']:.2f} | {test['mae_g_m2']:.2f} | {test['median_absolute_error_g_m2']:.2f} | {test['bias_g_m2']:.2f} |")
    lines += ["", f"Weather improvement gate on holdout: **{evaluation['weather_holdout_gate']}**.", "",
              "This gate concerns promotion of the weather claim; it does not retrospectively change the validation-selected candidate.", "",
              "## Partition counts", "", "| Partition | Observations | Field-seasons | Sampling dates | Provider grid/date groups |", "|---|---:|---:|---:|---:|"]
    for part, counts in evaluation["partitions"].items():
        lines.append(f"| {part} | {counts['observations']} | {counts['field_seasons']} | {counts['sampling_dates']} | {counts.get('weather_grid_date_groups', 'unknown')} |")
    if "weather_independence" in evaluation:
        audit = evaluation["weather_independence"]
        lines += ["", f"Static dataset: {audit['provider_grid_cells']} provider grid cells and {audit['provider_grid_date_groups']} distinct grid/date groups. "
                  f"Train and validation share {audit['train_validation_shared_grid_dates']} grid/date groups. "
                  "Request-window IDs are location-specific cache keys, not independent weather samples. "
                  "The frozen split was retained; this dependence weakens validation evidence."]
    lines += ["", "## Limits", ""] + [f"- {item}" for item in evaluation["limitations"]]
    if "age_support" in evaluation:
        support = evaluation["age_support"]
        lines += ["", "## Age support in the temporal holdout", "",
                  f"Training ages: {support['training_age_min']:.0f}–{support['training_age_max']:.0f} days. "
                  f"Held-out rows inside that range: {support['test_in_age_range']}; outside: {support['test_outside_age_range']}.", "",
                  "All held-out rows remain in the primary table. Age-supported subgroup errors are also saved in evaluation.json; they do not replace the full test results."]
        if support["within_age_range_metrics"]:
            lines += ["", "| Candidate | Within-age-range n | Within-age-range MAE |", "|---|---:|---:|"]
            for name, metrics in support["within_age_range_metrics"].items():
                lines.append(f"| {name} | {metrics['n']} | {metrics['mae_g_m2']:.2f} |")
    lines += ["", "No confidence percentage or prediction interval is estimated. Reanalysis evaluation is retrospective, not evidence of operational availability at past issue times.", "",
              "See evaluation.json for age/season errors, candidate configuration, data hashes, and selection evidence.", ""]
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--predictions", type=Path, default=ML_ROOT / "reports/wheat_v1/predictions.csv")
    args = parser.parse_args()
    frame = pd.read_csv(args.predictions)
    result = {f"{model}/{partition}": evaluate_predictions(group, group.prediction_g_m2)
              for (model, partition), group in frame.groupby(["model", "partition"])}
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
