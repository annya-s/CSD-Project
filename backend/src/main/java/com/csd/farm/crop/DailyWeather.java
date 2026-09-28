package com.csd.farm.crop;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// One complete local day's API data, not a current reading or a future forecast.
// Null means unavailable. Never substitute zero for missing API values.
public record DailyWeather(
        @NotNull LocalDate date,
        @NotBlank String timezone,
        @DecimalMin("-100") @DecimalMax("100") Double temperatureMeanC,
        @DecimalMin("0") @DecimalMax("86400") Double sunshineDurationSeconds,
        @DecimalMin("0") @DecimalMax("100") Double humidityMeanPercent,
        @DecimalMin("0") @DecimalMax("1") Double soilMoistureMeanM3M3) {

    public void validateCompletedDay() {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid weather timezone, such as Asia/Singapore.");
        }
        if (!date.isBefore(LocalDate.now(zone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply a completed day before today in the weather timezone.");
        }
    }
}
