package com.csd.farm.crop.crop_category;

import com.csd.farm.crop.CropType;

public abstract class Crop {
    protected CropType crop;
    protected CropRange temperatureRange;
    protected CropRange sunshineRange;

    public String temperatureIssue(String reading) {
        if (reading == null) {
            return null;
        }

        switch(reading) {
            case "BELOW_RANGE_SEVERE":
                return "Daily mean temperature is significantly below this crop's reference range.";
            case "BELOW_RANGE":
                return "Daily mean temperature is below this crop's reference range.";
            case "ABOVE_RANGE":
                return "Daily mean temperature is above this crop's reference range.";
            case "ABOVE_RANGE_SEVERE":
                return "Daily mean temperature is significantly above this crop's reference range.";
            default:
                break;
        }

        return null;
    }

    public String sunshineIssue(String reading) {
        if (reading == null) {
            return null;
        }

        switch(reading) {
            case "BELOW_RANGE_SEVERE":
                return "Daily sunshine is significantly below this crop's reference range.";
            case "BELOW_RANGE":
                return "Daily sunshine is below this crop's reference range.";
            case "ABOVE_RANGE":
                return "Daily sunshine is above this crop's reference range.";
            case "ABOVE_RANGE_SEVERE":
                return "Daily sunshine is significantly above this crop's reference range.";
            default:
                break;
        }

        return null;
    }

    /**
     * Checks if the AI recommendation is valid and how confident it is in its assessment.
     * Returns 0 if AI action doesn't exist.
     */
    public abstract Double checkAction(String temperatureReading, String sunshineReading, String aiOutput);
}
