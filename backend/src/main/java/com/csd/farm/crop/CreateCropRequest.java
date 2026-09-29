package com.csd.farm.crop;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record CreateCropRequest(
        @NotNull CropType cropType,
        @NotNull @PastOrPresent OffsetDateTime plantedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90")
        @Digits(integer = 3, fraction = 6) Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Digits(integer = 3, fraction = 6) Double longitude) {
}
