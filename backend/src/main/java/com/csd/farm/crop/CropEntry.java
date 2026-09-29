package com.csd.farm.crop;

import java.time.OffsetDateTime;

public record CropEntry(
        CropType cropType,
        OffsetDateTime plantedAt,
        double latitude,
        double longitude) {
}
