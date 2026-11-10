package com.csd.farm.crop;

import java.util.Map;

// Temporary reference data. Later, this lookup can read from Supabase instead.
// Sources and assumptions: docs/crop-growing-conditions.md.
public final class CropConditions {

    // Represents the optimal growing conditions of a set list of crops. 
    private static final Map<CropType, Conditions> CONDITIONS = Map.of(
            CropType.POTATO, new Conditions(new Range(15, 25, 7, 30),
            new Range(6, 8, 5, 9)),
            CropType.SUGAR_CANE, new Conditions(new Range(24, 37, temperatureTolerance), 
            new Range(7, 9, sunshineHoursTolerance)),
            CropType.APPLE, new Conditions(new Range(14, 27, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)),
            CropType.RICE, new Conditions(new Range(20, 30, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)),
            CropType.WHEAT, new Conditions(new Range(15, 23, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)),
            CropType.MAIZE, new Conditions(new Range(18, 33, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)),
            CropType.TOMATO, new Conditions(new Range(20, 27, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)),
            CropType.CARROT, new Conditions(new Range(15, 24, temperatureTolerance),
             new Range(4, 6, sunshineHoursTolerance)),
            CropType.LETTUCE, new Conditions(new Range(12, 21, temperatureTolerance), 
            new Range(4, 6, sunshineHoursTolerance)),
            CropType.SOYBEAN, new Conditions(new Range(20, 33, temperatureTolerance), 
            new Range(6, 8, sunshineHoursTolerance)));

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
    public record Range(double minimum, double maximum, double abs_minimum, double abs_maximum) { }
}
