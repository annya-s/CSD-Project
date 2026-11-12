package com.csd.farm.crop;

import java.util.Map;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.crops.*;

// Temporary reference data. Later, this lookup can read from Supabase instead.
// Sources and assumptions: docs/crop-growing-conditions.md.
public final class CropConditions {

    // Represents the optimal growing conditions of a set list of crops. 
    private static final Map<CropType, Crop> CONDITIONS = Map.of(
        CropType.POTATO, new Potato(),
        CropType.SUGAR_CANE, new SugarCane(),
        CropType.APPLE, new Apple(),
        CropType.RICE, new Rice(),
        CropType.WHEAT, new Wheat(),
        CropType.MAIZE, new Maize(),
        CropType.TOMATO, new Tomato(),
        CropType.CARROT, new Carrot(),
        CropType.LETTUCE, new Lettuce(),
        CropType.SOYBEAN, new SoyBean()
    );

    // Default constructor.
    private CropConditions() { }

    /**
     * Returns to oprimal growing conditions for a certain crop.
     * @param cropType The type of crop.
     * @return A Conditions representing the optimal growing conditions.
     */
    public static Crop forCrop(CropType cropType) {
        return CONDITIONS.get(cropType);
    }

    public record Range(double minimum, double maximum) {
        
    }
}
