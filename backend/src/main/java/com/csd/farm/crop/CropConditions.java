package com.csd.farm.crop;

import java.util.Map;

// Temporary reference data. Later, this lookup can read from Supabase instead.
// Sources and assumptions: docs/crop-growing-conditions.md.
public final class CropConditions {

    // Represents the optimal growing conditions of a set list of crops. 
    private static final Map<CropType, Conditions> CONDITIONS = Map.of(
            CropType.POTATO, new Conditions(new Range(15, 25), new Range(6, 8)),
            CropType.SUGAR_CANE, new Conditions(new Range(24, 37), new Range(7, 9)),
            CropType.APPLE, new Conditions(new Range(14, 27), new Range(6, 8)),
            CropType.RICE, new Conditions(new Range(20, 30), new Range(6, 8)),
            CropType.WHEAT, new Conditions(new Range(15, 23), new Range(6, 8)),
            CropType.MAIZE, new Conditions(new Range(18, 33), new Range(6, 8)),
            CropType.TOMATO, new Conditions(new Range(20, 27), new Range(6, 8)),
            CropType.CARROT, new Conditions(new Range(15, 24), new Range(4, 6)),
            CropType.LETTUCE, new Conditions(new Range(12, 21), new Range(4, 6)),
            CropType.SOYBEAN, new Conditions(new Range(20, 33), new Range(6, 8)));

    // Default constructor.
    private CropConditions() { }

    /**
     * Returns to oprimal growing conditions for a certain crop.
     * @param cropType The type of crop.
     * @return A Conditions representing the optimal growing conditions.
     */
    public static Conditions forCrop(CropType cropType) {
        return CONDITIONS.get(cropType);
    }

    // Humidity and soil moisture deliberately have no numerical reference ranges yet.
    public record Conditions(Range temperatureC, Range sunshineHours) { }

    // Represents a range between two doubles.
    public record Range(double minimum, double maximum) { }
}
