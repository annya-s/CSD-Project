package com.csd.farm.crop;

import java.util.Map;

// Temporary reference data. Later, this lookup can read from Supabase instead.
// Sources and assumptions: docs/crop-growing-conditions.md.
public final class CropConditions {

    // Represents the optimal growing conditions of a set list of crops. 
    private static final Map<CropType, Conditions> CONDITIONS = Map.of(
            CropType.POTATO, new Conditions(new Range(15, 25, 7, 30),
            new Range(6, 8, 5, 9)),
            CropType.SUGAR_CANE, new Conditions(new Range(24, 37, 15, 41),
            new Range(7, 9, 6, 8)),
            CropType.APPLE, new Conditions(new Range(14, 27, 8, 33), 
            new Range(6, 8, 5, 9)),
            CropType.RICE, new Conditions(new Range(20, 30, 10, 36), 
            new Range(6, 8, 5, 9)),
            CropType.WHEAT, new Conditions(new Range(15, 23, 5, 27), 
            new Range(6, 8, 5, 9)),
            CropType.MAIZE, new Conditions(new Range(18, 33, 10, 47), 
            new Range(6, 8, 5, 9)),
            CropType.TOMATO, new Conditions(new Range(20, 27, 7, 35), 
            new Range(6, 8, 5, 9)),
            CropType.CARROT, new Conditions(new Range(15, 24, 3, 30),
             new Range(4, 6, 3, 7)),
            CropType.LETTUCE, new Conditions(new Range(12, 21, 5, 30), 
            new Range(4, 6, 3, 7)),
            CropType.SOYBEAN, new Conditions(new Range(20, 33, 10, 38), 
            new Range(6, 8, 5, 9)));

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
    public record Range(double minimum, double maximum, double absMinimum, double absMaximum) { }
}
