"""Contract checks for installed synthetic models; no database or network required."""
import re
import sys
import unittest
from unittest.mock import patch
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from average_prediction import PROFILES, compare


class AveragePredictionTests(unittest.TestCase):
    def request(self, crop):
        today = date(2026, 11, 10)
        t, soil, vpd, sun = PROFILES[crop]['reference']
        return dict(crop=crop, prediction_date=str(today),
                    sowing_date=str(today-timedelta(days=30)),
                    start_date=str(today-timedelta(days=14)),
                    end_date=str(today-timedelta(days=1)),
                    temperature=t, soil=soil, vpd=vpd, sunshine_seconds=sun*3600)

    def test_every_reference_crop_has_model_and_correct_unit(self):
        root = Path(__file__).resolve().parents[2]
        source = (root/'backend/src/main/java/com/csd/farm/crop/CropConditions.java').read_text()
        self.assertEqual(set(re.findall(r'CropType\.([A-Z_]+),', source)), set(PROFILES))
        for crop, profile in PROFILES.items():
            with self.subTest(crop=crop):
                result = compare(self.request(crop))
                self.assertEqual(result['status'], 'SYNTHETIC_DEMO')
                self.assertEqual(result['unit'], profile['unit'])
                self.assertGreaterEqual(result['value'], 0)
                self.assertAlmostEqual(result['reference_conditions'],
                                       profile['mature_target'])
                self.assertEqual(result['projection_date'], str(date(2026, 10, 11)+timedelta(days=PROFILES[crop]['maturity_days'])))
                self.assertLessEqual(result.get('value') or 0, PROFILES[crop]['mature_target'])

    def test_invalid_and_stale_data(self):
        for key, value in [('soil', True), ('temperature', float('nan')),
                           ('sunshine_seconds', -1), ('start_date', '2026-10-01')]:
            request = self.request('CARROT')
            request[key] = value
            self.assertEqual(compare(request)['status'], 'INVALID_INPUT')
        request = self.request('CARROT')
        request['sowing_date'] = '2026-11-11'
        self.assertEqual(compare(request)['status'], 'UNSUPPORTED')

    def test_hot_dry_scenario_is_different(self):
        for crop in PROFILES:
            request = self.request(crop)
            request.update(temperature=39, soil=.03, vpd=4)
            result = compare(request)
            self.assertLess(result['value'], result['reference_conditions'], crop)

    def test_target_without_weather_or_model_files(self):
        with patch('average_prediction.FOLDER', Path('missing-model-test-directory')):
            for crop in PROFILES:
                request = self.request(crop)
                request = {k: request[k] for k in ['crop', 'prediction_date', 'sowing_date']}
                request['mode'] = 'reference'
                result = compare(request)
                self.assertEqual(result['status'], 'REFERENCE_ONLY', crop)
                self.assertIsNone(result['value'])
                self.assertGreater(result['reference_conditions'], 0)
                self.assertEqual(result['projection_date'], str(date(2026, 10, 11)+timedelta(days=PROFILES[crop]['maturity_days'])))
                self.assertLessEqual(result.get('value') or 0, PROFILES[crop]['mature_target'])

    def test_saved_cold_weather_produces_prediction(self):
        request = self.request('CARROT')
        request.update(temperature=-28.53125, soil=.339,
                       vpd=.0142857142857142, sunshine_seconds=1545.68642857143)
        result = compare(request)
        self.assertEqual(result['status'], 'SYNTHETIC_DEMO')
        self.assertGreaterEqual(result['value'], 0)
        self.assertLess(result['value'], result['reference_conditions'])

    def test_full_growth_target_is_independent_of_current_age(self):
        for crop in PROFILES:
            first = self.request(crop)
            later = dict(first, sowing_date='2026-10-01')
            self.assertEqual(compare(first)['reference_conditions'], compare(later)['reference_conditions'])


if __name__ == '__main__':
    unittest.main()
