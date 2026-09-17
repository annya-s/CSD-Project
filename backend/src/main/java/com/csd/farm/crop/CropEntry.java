package com.csd.farm.crop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CropEntry(
        CropType cropType,
        OffsetDateTime plantedAt,
        BigDecimal latitude,
        BigDecimal longitude) {
}
