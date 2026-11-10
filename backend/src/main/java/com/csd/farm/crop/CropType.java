package com.csd.farm.crop;

// Supported crop types. Growing conditions are stored in CropConditions.
public enum CropType {
    POTATO("Potato"),
    SUGAR_CANE("Sugar cane"),
    APPLE("Apple"),
    RICE("Rice"),
    WHEAT("Wheat"),
    MAIZE("Maize"),
    TOMATO("Tomato"),
    CARROT("Carrot"),
    LETTUCE("Lettuce"),
    SOYBEAN("Soybean"),
    SUGAR_BEET("Sugar beet"),
    SPRING_WHEAT("Spring wheat"),
    FABA_BEAN("Faba bean"),
    WINTER_BARLEY("Winter barley"),
    RAPESEED("Rapeseed"),
    SPELT("Spelt");

    private final String displayName;

    /**
     * The single constructor for a CropType.
     * @param displayName String representing the name of a crop.
     */
    CropType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the name of the crop as a String.
     * @return The name of the crop as a String.
     */
    public String displayName() {
        return displayName;
    }
}