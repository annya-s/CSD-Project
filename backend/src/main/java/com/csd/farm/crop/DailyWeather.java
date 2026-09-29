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

// Daily averages and a sunshine total. Automatic data is today's full-day forecast.
// Null means unavailable. Never substitute zero for missing API values.
public record DailyWeather(
        @NotNull LocalDate date,
        @NotBlank String timezone,
        @DecimalMin("-100") @DecimalMax("100") Double temperatureMeanC,
        @DecimalMin("0") @DecimalMax("86400") Double sunshineDurationSeconds,
        @DecimalMin("0") @DecimalMax("100") Double humidityMeanPercent,
        @DecimalMin("0") @DecimalMax("1") Double soilMoistureMeanM3M3,
        @DecimalMin("0") Double vapourPressureDeficitMeanKpa) {

    // Existing database snapshots do not contain VPD. Do not infer it from humidity.
    public DailyWeather(LocalDate date, String timezone, Double temperatureMeanC,
                        Double sunshineDurationSeconds, Double humidityMeanPercent,
                        Double soilMoistureMeanM3M3) {
        this(date, timezone, temperatureMeanC, sunshineDurationSeconds, humidityMeanPercent,
                soilMoistureMeanM3M3, null);
    }

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
