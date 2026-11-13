package com.csd.farm.crop.crop_category.crops;

import com.csd.farm.crop.CropType;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.CropRange;
import com.csd.farm.crop.crop_category.Guideline;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;

import java.util.Map;

public class Tomato extends Crop {
    public Tomato() {
        super.crop = CropType.TOMATO;
        super.temperatureRange = new CropRange(20, 27, 7, 35);
        super.sunshineRange = new CropRange(6, 8, 5, 9);
        super.temperatureGuidelines = Map.of(
            "BELOW_RANGE_SEVERE", new Guideline(Map.of(RELOCATE, 0.3, REMOVE_DAMAGED, 0.1, PROTECT_FROM_COLD, 0.1), COLD_HARMS),
            "BELOW_RANGE", new Guideline(Map.of(RELOCATE, 0.2, PROTECT_FROM_COLD, 0.1), COLD_HARMS),
            "ABOVE_RANGE", new Guideline(Map.of(RELOCATE, 0.1, WATER, 0.1), HEAT_HARMS),
            "ABOVE_RANGE_SEVERE", new Guideline(Map.of(RELOCATE, 0.2, REMOVE_DAMAGED, 0.1, WATER, 0.2), HEAT_HARMS));
        super.sunshineGuidelines = MOVABLE_SUNSHINE;
    }
}
