package com.csd.farm.crop.crop_category.crops;

import com.csd.farm.crop.CropType;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.CropRange;
import com.csd.farm.crop.crop_category.Guideline;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;

import java.util.Map;

public class Rice extends Crop {
    public Rice() {
        super.crop = CropType.RICE;
        super.temperatureRange = new CropRange(20, 30, 10, 36);
        super.sunshineRange = new CropRange(6, 8, 5, 9);
        super.temperatureGuidelines = Map.of(
            "BELOW_RANGE_SEVERE", new Guideline(Map.of(PROTECT_FROM_COLD, 0.3, REMOVE_DAMAGED, 0.1), COLD_HARMS),
            "BELOW_RANGE", new Guideline(Map.of(PROTECT_FROM_COLD, 0.3), COLD_HARMS),
            "ABOVE_RANGE", new Guideline(Map.of(SHADE, 0.1, WATER, 0.1), HEAT_HARMS),
            "ABOVE_RANGE_SEVERE", new Guideline(Map.of(SHADE, 0.3, REMOVE_DAMAGED, 0.1, WATER, 0.2), HEAT_HARMS));
        super.sunshineGuidelines = FIELD_SUNSHINE;
    }
}
