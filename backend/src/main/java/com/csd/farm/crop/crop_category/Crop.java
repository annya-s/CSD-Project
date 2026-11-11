package com.csd.farm.crop.crop_category;

import com.csd.farm.crop.CropType;

public abstract class Crop {
    private CropType crop;
    private CropRange temperatureRange;
    private CropRange sunshineRange;

    abstract String temperatureIssue(String reading);
    abstract String temperatureRecommendedAction(String reading);
    abstract String sunshineIssue(String reading);
    abstract String sunshineRecommendedAction(String reading);
}
