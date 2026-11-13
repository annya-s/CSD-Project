package com.csd.farm.crop.crop_category.crops;

import com.csd.farm.crop.CropType;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.CropRange;
import com.csd.farm.crop.crop_category.Guideline;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;

import java.util.Map;

public class Carrot extends Crop {
    public Carrot() {
        super.crop = CropType.CARROT;
        super.temperatureRange = new CropRange(15, 24, 3, 30);
        super.sunshineRange = new CropRange(4, 6, 3, 7);
        super.temperatureGuidelines = Map.of(
            "BELOW_RANGE_SEVERE", new Guideline(Map.of(PROTECT_FROM_COLD, 0.5, REMOVE_DAMAGED, 0.1), COLD_HARMS),
            "BELOW_RANGE", new Guideline(Map.of(PROTECT_FROM_COLD, 0.2), COLD_HARMS),
            "ABOVE_RANGE", new Guideline(Map.of(WATER, 0.1), HEAT_HARMS),
            "ABOVE_RANGE_SEVERE", new Guideline(Map.of(REMOVE_DAMAGED, 0.1, WATER, 0.2), HEAT_HARMS));
        super.sunshineGuidelines = FIELD_SUNSHINE;
    }
}
