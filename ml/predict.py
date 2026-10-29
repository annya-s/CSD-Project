"""Predict biomass from a local JSON request using the saved five-feature model."""
import argparse
import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from data_preprocessing import ROOT, FEATURES, PROFILE, INPUT_ERROR, build_features, read_json, write_json


def predict(request, model_dir=ROOT/'model'):
    try:
        features = build_features(request)
    except ValueError:
        return {'status': 'INVALID_INPUT', 'value': None, 'message': INPUT_ERROR}
    try:
        folder = Path(model_dir)
        metadata = read_json(folder/'metadata.json')
        path = folder/'model.joblib'
        if metadata['profile'] != PROFILE or metadata['features'] != FEATURES:
            raise ValueError()
        if metadata['model_sha256'] != hashlib.sha256(path.read_bytes()).hexdigest():
            raise ValueError()
        if metadata['input_features'] not in (FEATURES, ['days_since_sowing']):
            raise ValueError()
        if metadata['target'] != 'target_dry_biomass_g_m2' or metadata['unit'] != 'g/m2':
            raise ValueError()
        context = dict(features, latitude=request['latitude'], longitude=request['longitude'])
        outside = [key for key, value in context.items()
                   if not metadata['training_ranges'][key]['min'] <= value <= metadata['training_ranges'][key]['max']]
        if outside:
            return {'status': 'UNSUPPORTED', 'value': None,
                    'message': 'Inputs are outside the training region or feature ranges.', 'outside_ranges': outside}
        # Only load our locally trained file. Never accept an uploaded joblib model.
        model = joblib.load(path)
        value = float(model.predict(pd.DataFrame([features])[metadata['input_features']])[0])
        if not np.isfinite(value):
            raise ValueError()
    except (OSError, ValueError, KeyError, TypeError, EOFError, ImportError, AttributeError):
        return {'status': 'UNAVAILABLE', 'value': None, 'message': 'Train the five-feature model before predicting.'}
    return {'status': 'EXPERIMENTAL', 'kind': 'DRY_ABOVEGROUND_BIOMASS', 'value': max(0, value),
            'unit': 'g/m2', 'confidence': None, 'model_version': metadata['model_version'],
            'selected_model': metadata['selected_model'], 'prediction_date': request['prediction_date'],
            'features': features,
            'advice': ['Compare estimated growth with field observations; biomass is not a health score.',
                       'Check root-zone moisture and recent irrigation before watering; shallow modelled soil moisture is not an irrigation prescription.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--model', type=Path, default=ROOT/'model')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = predict(read_json(args.input), args.model)
        if args.output:
            write_json(args.output, result)
    except (ValueError, OSError, TypeError):
        result = {'status': 'INVALID_INPUT', 'value': None, 'message': INPUT_ERROR}
    print(json.dumps(result, allow_nan=False))
    if result['status'] in ('INVALID_INPUT', 'UNAVAILABLE'):
        raise SystemExit(1)
