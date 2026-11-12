package com.csd.farm.crop;

public record FarmLocation (
        Long farmId,
        double latitude,
        double longitude
) {}