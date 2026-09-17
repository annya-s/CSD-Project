package com.csd.farm.crop;

// The fixed crop catalogue. Add new types here and in a new database migration.
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
    SOYBEAN("Soybean");

    private final String displayName;

    CropType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
