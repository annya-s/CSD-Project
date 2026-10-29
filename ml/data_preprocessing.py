"""Read local or database daily weather and create the five biomass features."""
import argparse
import json
import math
import os
from datetime import date, datetime, timedelta
from pathlib import Path
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import pandas as pd
import requests

ROOT = Path(__file__).resolve().parent
PROFILE = 'ifs-daily-soil-0-7-v2'
FEATURES = ['days_since_sowing', 'temperature_min_mean_14d',
            'temperature_max_mean_14d', 'soil_moisture_mean_14d',
            'solar_radiation_total_14d']
WEATHER = ['temperature_min_c', 'temperature_max_c',
           'soil_moisture_m3_m3', 'solar_radiation_mj_m2']
INPUT_ERROR = 'Check the planting details and provide 14 complete days of valid weather data.'


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, allow_nan=False), encoding='utf-8')


def build_features(request):
    """Same calculations for training and prediction; never fill missing readings."""
    try:
        target = date.fromisoformat(request['prediction_date'])
        sowing = date.fromisoformat(request['sowing_date'])
        zone = ZoneInfo(request['timezone'])
        if request['profile'] != PROFILE or request['crop'] != 'winter_wheat':
            raise ValueError()
        if request['growing_system'] != 'outdoor_soil':
            raise ValueError()
        if not 0 < (target - sowing).days or target > datetime.now(zone).date():
            raise ValueError()
        for key, bound in [('latitude', 90), ('longitude', 180)]:
            value = request[key]
            if type(value) not in (int, float) or not math.isfinite(value) or abs(value) > bound:
                raise ValueError()
        rows = request['weather']
        expected = [(target - timedelta(days=i)).isoformat() for i in range(14, 0, -1)]
        if len(rows) != 14 or [r['date'] for r in rows] != expected:
            raise ValueError()
        for row in rows:
            if any(type(row[k]) not in (int, float) or not math.isfinite(row[k]) for k in WEATHER):
                raise ValueError()
            if not -100 <= row['temperature_min_c'] <= row['temperature_max_c'] <= 70:
                raise ValueError()
            if not 0 <= row['soil_moisture_m3_m3'] <= 1 or row['solar_radiation_mj_m2'] < 0:
                raise ValueError()
        return dict(zip(FEATURES, [(target-sowing).days,
            sum(r['temperature_min_c'] for r in rows)/14,
            sum(r['temperature_max_c'] for r in rows)/14,
            sum(r['soil_moisture_m3_m3'] for r in rows)/14,
            sum(r['solar_radiation_mj_m2'] for r in rows)]))
    except (ValueError, KeyError, TypeError, OverflowError, AttributeError):
        raise ValueError(INPUT_ERROR) from None


def read_database(request):
    """Explicit read-only Supabase call. Caller must verify the farmer owns this cycle."""
    try:
        base = os.environ['SUPABASE_URL'].rstrip('/')
        key = os.environ['SUPABASE_SECRET_KEY']
        parsed = urlparse(base)
        if parsed.scheme != 'https' or not parsed.netloc or parsed.path or parsed.query or parsed.fragment:
            raise ValueError()
        cycle = request['crop_cycle_id']
        if type(cycle) is not int or cycle <= 0 or request['profile'] != PROFILE:
            raise ValueError()
        target = date.fromisoformat(request['prediction_date'])
    except (KeyError, TypeError, ValueError):
        raise ValueError('Check database configuration and the trusted crop-cycle details.') from None
    headers = {'apikey': key, 'Prefer': 'count=exact'}
    if key.startswith('eyJ'):
        headers['Authorization'] = 'Bearer ' + key
    params = [('select', 'date,' + ','.join(WEATHER)),
              ('crop_cycle_id', f'eq.{cycle}'), ('profile', f'eq.{PROFILE}'),
              ('date', 'gte.' + (target-timedelta(days=14)).isoformat()),
              ('date', 'lt.' + target.isoformat()), ('order', 'date.asc'), ('limit', '15')]
    try:
        response = requests.get(base + '/rest/v1/ml_daily_weather', params=params,
                                headers=headers, timeout=(5, 30), allow_redirects=False)
        response.raise_for_status()
        rows = response.json()
        if response.status_code != 200 or response.headers.get('Content-Range', '').split('/')[-1] != '14':
            raise ValueError(INPUT_ERROR)
    except requests.RequestException:
        raise RuntimeError('Database weather is unavailable.') from None
    result = dict(request, weather=rows)
    build_features(result)
    return result


def prepare_training():
    """Use the existing observed labels and frozen split; no web downloads."""
    observations = pd.read_csv(ROOT/'data/processed/observations.csv')
    weather = read_json(ROOT/'data/processed/daily_weather.json')
    splits = read_json(ROOT/'data/splits/wheat_v1.json')['assignments']
    records = []
    for row in observations.to_dict('records'):
        visit = row['visit_id']
        if visit not in weather:
            continue
        context = dict(crop='winter_wheat', growing_system='outdoor_soil', profile=PROFILE,
                       timezone='Europe/Sofia', sowing_date=row['sowing_date'],
                       prediction_date=row['target_date'], latitude=row['latitude'],
                       longitude=row['longitude'], weather=weather[visit])
        records.append({**row, **build_features(context), 'profile': PROFILE,
                        'partition': splits[visit]})
    if not records:
        raise ValueError('No matching observed training data.')
    frame = pd.DataFrame(records)
    frame.to_csv(ROOT/'data/training.csv', index=False)
    return frame


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--request', type=Path, help='Planting context and optionally local weather JSON')
    parser.add_argument('--database', action='store_true', help='Read weather from Supabase explicitly')
    parser.add_argument('--output', type=Path, default=ROOT/'data/prepared_request.json')
    args = parser.parse_args()
    try:
        if args.request:
            request = read_json(args.request)
            if args.database:
                request = read_database(request)
            features = build_features(request)
            write_json(args.output, request)
            print(json.dumps({'status': 'READY', 'features': features, 'saved_to': str(args.output)}))
        elif args.database:
            raise ValueError('Provide --request with trusted crop-cycle details.')
        else:
            print(json.dumps({'status': 'READY', 'training_rows': len(prepare_training())}))
    except (ValueError, OSError, RuntimeError, KeyError) as error:
        message = str(error) if isinstance(error, (ValueError, RuntimeError)) else 'Required local data is unavailable.'
        print(json.dumps({'status': 'ERROR', 'message': message}))
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
