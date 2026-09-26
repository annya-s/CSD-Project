package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EnvironmentReading(
    LocalDateTime timestamp,
    Double temperature,
    Double soilMoisture,
    LocalDate sunshineDate,
    Double sunshineDuration
) {}