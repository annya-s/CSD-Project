"""Train a small CatBoost biomass model and compare it with an age-only baseline."""
import argparse
import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from catboost import CatBoostRegressor
from sklearn.linear_model import Ridge
from sklearn.preprocessing import PolynomialFeatures, StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.metrics import mean_absolute_error

from data_preprocessing import ROOT, FEATURES, PROFILE, write_json

TARGET = 'target_dry_biomass_g_m2'


def train(path, output):
    frame = pd.read_csv(path)
    if frame.empty or not frame.data_origin.eq('OBSERVED').all() or not frame.profile.eq(PROFILE).all():
        raise ValueError('Training requires observed biomass labels and the five-feature profile.')
    values = frame[FEATURES + [TARGET, 'latitude', 'longitude']].to_numpy(float)
    if not np.isfinite(values).all() or (frame[TARGET] < 0).any() or frame.visit_id.duplicated().any():
        raise ValueError('Training data contains invalid values or duplicate observations.')
    if (frame.days_since_sowing <= 0).any() or not frame.soil_moisture_mean_14d.between(0, 1).all():
        raise ValueError('Invalid planting age or soil moisture.')
    if (frame.temperature_min_mean_14d > frame.temperature_max_mean_14d).any() or (frame.solar_radiation_total_14d < 0).any():
        raise ValueError('Invalid temperature or radiation.')
    if set(frame.partition) != {'train', 'validation', 'test'}:
        raise ValueError('Provide nonempty train, validation and test partitions.')
    if frame.groupby('field_season_id').partition.nunique().max() != 1:
        raise ValueError('A field-season must not appear in multiple partitions.')
    parts = {p: frame[frame.partition.eq(p)] for p in ('train', 'validation', 'test')}
    models = {
        'age_only': make_pipeline(PolynomialFeatures(2, include_bias=False), StandardScaler(), Ridge(alpha=10)),
        'catboost': CatBoostRegressor(iterations=200, depth=3, learning_rate=.05, l2_leaf_reg=5,
                    loss_function='RMSE', random_seed=42, thread_count=2, verbose=False, allow_writing_files=False)}
    metrics = {}
    for name, model in models.items():
        columns = ['days_since_sowing'] if name == 'age_only' else FEATURES
        model.fit(parts['train'][columns], parts['train'][TARGET])
        metrics[name] = {p: float(mean_absolute_error(parts[p][TARGET],
                        np.maximum(0, model.predict(parts[p][columns])))) for p in ('validation', 'test')}
    # Selection never uses the test errors; keep the simple model on a tie.
    selected = min(models, key=lambda name: metrics[name]['validation'])
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    model_path = output/'model.joblib'
    joblib.dump(models[selected], model_path)
    metadata = dict(model_version='wheat-biomass-five-v2', profile=PROFILE,
        features=FEATURES, input_features=['days_since_sowing'] if selected == 'age_only' else FEATURES,
        selected_model=selected, crop='winter_wheat', growing_system='outdoor_soil',
        unit='g/m2', target=TARGET, model_sha256=hashlib.sha256(model_path.read_bytes()).hexdigest(),
        data_sha256=hashlib.sha256(Path(path).read_bytes()).hexdigest(),
        training_ranges={c: {'min': float(parts['train'][c].min()), 'max': float(parts['train'][c].max())}
                         for c in FEATURES + ['latitude', 'longitude']},
        metrics_mae_g_m2=metrics, counts={p: len(rows) for p, rows in parts.items()},
        limitations=['Experimental biomass, not health or water stress.',
                    'One Bulgarian winter-wheat region; not validated for other crops.',
                    'The historical test season has been inspected previously; this is a comparison, not a new blind test.',
                    'Nearby fields share weather; field grouping alone does not ensure independent weather samples.',
                    'No calibrated confidence or prediction interval.'])
    write_json(output/'metadata.json', metadata)
    return metadata


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path, default=ROOT/'data/training.csv')
    parser.add_argument('--output', type=Path, default=ROOT/'model')
    args = parser.parse_args()
    try:
        result = train(args.data, args.output)
        print(json.dumps({'status': 'TRAINED', 'selected_model': result['selected_model'],
                          'metrics_mae_g_m2': result['metrics_mae_g_m2']}))
    except (ValueError, KeyError, OSError, TypeError):
        print(json.dumps({'status': 'ERROR', 'message': 'Check the labelled training CSV. Run data_preprocessing.py first.'}))
        raise SystemExit(1) from None
