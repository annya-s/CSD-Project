package com.csd.farm.crop;

// The fixed crop catalogue. Add new types here and in a new database migration.
public enum CropType {
    POTATO("Potato", 15, 25, 5.0, 6.2, 500, 800, new int[] {35}),
    SUGAR_CANE("Sugar cane", 24, 37, 5.0, 8.0, 1500, 2000, new int[] {65}),
    APPLE("Apple", 14, 27, 6.0, 7.0, 700, 2500, new int[] {50}),
    RICE("Rice", 20, 30, 5.5, 7.0, 1500, 2000, new int[] {}),
    WHEAT("Wheat", 15, 23, 6.0, 7.0, 750, 900, new int[] {55}),
    MAIZE("Maize", 18, 33, 5.0, 7.0, 600, 1200, new int[] {55, 50}),
    TOMATO("Tomato", 20, 27, 5.5, 6.8, 600, 1300, new int[] {40}),
    CARROT("Carrot", 15, 24, 5.8, 6.8, 600, 1200, new int[] {35}),
    LETTUCE("Lettuce", 12, 21 ,6.0, 7.0, 1100, 1400, new int[] {30}),
    SOYBEAN("Soybean", 20, 33, 5.5, 6.5, 600, 1500, new int[] {50});

    private final String displayName;

    private final int optimalTempMin;
    private final int optimalTempMax;

    private final double optimalPhMin;
    private final double optimalPhMax;

    private final int annualRainfallMin;
    private final int annualRainfallMax;

    private final int[] availableRootZoneWaterPercentage;

    CropType(String displayName, int optimalTempMin, int optimalTempMax,
        double optimalPhMin, double optimalPhMax, int annualRainfallMin, int annualRainfallMax,
        int[] availableRootZoneWaterPercentage) {
        this.displayName = displayName;
        this.optimalTempMin = optimalTempMin;
        this.optimalTempMax = optimalTempMax;
        this.optimalPhMin = optimalPhMin;
        this.optimalPhMax = optimalPhMax;
        this.annualRainfallMin = annualRainfallMin;
        this.annualRainfallMax = annualRainfallMax;
        this.availableRootZoneWaterPercentage = availableRootZoneWaterPercentage;
    }

    public String displayName() {
        return displayName;
    }

    public int optimalTempMin() {
        return optimalTempMin;
    }

    public int optimalTempMax() {
        return optimalTempMax;
    }

    public double optimalPhMin() {
        return optimalPhMin;
    }

    public double optimalPhMax() {
        return optimalPhMax;
    }

    public int annualRainfallMin() {
        return annualRainfallMin;
    }

    public int annualRainfallMax() {
        return annualRainfallMax;
    }

    public int[] availableRootZoneWaterPercentage() {
        return availableRootZoneWaterPercentage;
    }
}
