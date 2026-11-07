package com.csd.farm.environment;

import java.time.LocalDate;

public record HistoricalReadingAverage(
    int cropCycleId,
    LocalDate startDate,
    LocalDate endDate,
    Double temperatureMean,
    Double soilMoistureMean,
    Double vpdMean,
    Double sunshineDurationMean
) {}