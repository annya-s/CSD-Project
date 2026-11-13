package com.csd.farm.crop.crop_category;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.csd.farm.crop.CropType;

public abstract class Crop {
    protected CropType crop;
    protected CropRange temperatureRange;
    protected CropRange sunshineRange;

    public CropType getCrop() {
        return crop;
    }

    public CropRange getTemperatureRange() {
        return temperatureRange;
    }

    public CropRange getSunshineRange() {
        return sunshineRange;
    }

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

    /** Status -> guideline for each variable; every out-of-range status must have an entry. */
    protected Map<String, Guideline> temperatureGuidelines;
    protected Map<String, Guideline> sunshineGuidelines;

    public Map<String, Guideline> getTemperatureGuidelines() {
        return temperatureGuidelines;
    }

    public Map<String, Guideline> getSunshineGuidelines() {
        return sunshineGuidelines;
    }

    /** The guidelines that apply to these statuses, or IN_RANGE (keep monitoring) when nothing is out of range. */
    public List<Guideline> guidelinesFor(String temperatureStatus, String sunshineStatus) {
        List<Guideline> active = new ArrayList<>();
        Guideline temperature = temperatureGuidelines.get(temperatureStatus);
        Guideline sunshine = sunshineGuidelines.get(sunshineStatus);
        if (temperature != null) active.add(temperature);
        if (sunshine != null) active.add(sunshine);
        return active.isEmpty() ? List.of(GuidelineAgreement.IN_RANGE) : active;
    }

    public static final List<String> OUT_OF_RANGE_STATUSES =
            List.of("BELOW_RANGE_SEVERE", "BELOW_RANGE", "ABOVE_RANGE", "ABOVE_RANGE_SEVERE");

    // Actions that make the stress worse.
    protected static final Set<RecommendedAction> COLD_HARMS = Set.of(SHADE);
    protected static final Set<RecommendedAction> HEAT_HARMS = Set.of(PROTECT_FROM_COLD, REDUCE_WATER);
    protected static final Set<RecommendedAction> DARK_HARMS = Set.of(SHADE);
    protected static final Set<RecommendedAction> BRIGHT_HARMS = Set.of(SUPPLEMENT_LIGHT);

    // Sunshine guidance is the same for all crops; only plants that can be moved get RELOCATE.
    protected static final Map<String, Guideline> FIELD_SUNSHINE = Map.of(
        "BELOW_RANGE_SEVERE", new Guideline(Map.of(SUPPLEMENT_LIGHT, 0.1), DARK_HARMS),
        "BELOW_RANGE", new Guideline(Map.of(MONITOR, 0.2), DARK_HARMS),
        "ABOVE_RANGE", new Guideline(Map.of(MONITOR, 0.1), BRIGHT_HARMS),
        "ABOVE_RANGE_SEVERE", new Guideline(Map.of(SHADE, 0.2), BRIGHT_HARMS));

    protected static final Map<String, Guideline> MOVABLE_SUNSHINE = Map.of(
        "BELOW_RANGE_SEVERE", new Guideline(Map.of(RELOCATE, 0.2, SUPPLEMENT_LIGHT, 0.1), DARK_HARMS),
        "BELOW_RANGE", new Guideline(Map.of(RELOCATE, 0.2), DARK_HARMS),
        "ABOVE_RANGE", new Guideline(Map.of(RELOCATE, 0.1), BRIGHT_HARMS),
        "ABOVE_RANGE_SEVERE", new Guideline(Map.of(RELOCATE, 0.2, SHADE, 0.2), BRIGHT_HARMS));
}
