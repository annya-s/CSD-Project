"""Contract checks for installed synthetic models; no database or network required."""
import re
import sys
import unittest
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
                self.assertAlmostEqual(result['value'], result['reference_conditions'])
                self.assertEqual(result['projection_date'], '2026-11-24')

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


if __name__ == '__main__':
    unittest.main()
