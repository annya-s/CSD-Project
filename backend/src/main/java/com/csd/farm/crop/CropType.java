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
    SOYBEAN("Soybean");

    private final String displayName;

    CropType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}