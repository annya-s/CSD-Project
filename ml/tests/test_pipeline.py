import copy
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pytest
from data_preprocessing import ROOT, build_features, read_json, FEATURES
from predict import predict

def request():
    return read_json(ROOT/'data/example_request.json')

def test_daily_aggregation():
    r = request()
    for row in r['weather']:
        row.update(temperature_min_c=10,temperature_max_c=20,soil_moisture_m3_m3=.25,solar_radiation_mj_m2=12)
    f = build_features(r)
    assert list(f) == FEATURES
    assert f['temperature_min_mean_14d'] == 10
    assert f['solar_radiation_total_14d'] == 168
    assert f['soil_moisture_mean_14d'] == .25

@pytest.mark.parametrize('problem', ['missing', 'duplicate', 'nan', 'depth', 'minmax'])
def test_invalid_inputs(problem):
    r = request()
    if problem == 'missing': r['weather'].pop()
    if problem == 'duplicate': r['weather'][1] = copy.deepcopy(r['weather'][0])
    if problem == 'nan': r['weather'][0]['solar_radiation_mj_m2'] = float('nan')
    if problem == 'depth': r['profile'] = 'soil-3-9'
    if problem == 'minmax': r['weather'][0]['temperature_min_c'] = 100
    assert predict(r)['status'] == 'INVALID_INPUT'

def test_model_and_support(tmp_path):
    r = request()
    assert predict(r, tmp_path)['status'] == 'UNAVAILABLE'
    result = predict(r)
    assert result['status'] == 'EXPERIMENTAL'
    assert result['value'] >= 0
    r['latitude'] = 1.35
    assert predict(r)['status'] == 'UNSUPPORTED'
