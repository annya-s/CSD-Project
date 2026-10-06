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

    /**
     * Constructor for a DailyWeather report from the database. Since it has no VPD,
     * we assume it to be null and pass to the other constructor.
     * @param date The date.
     * @param timezone The timezone.
     * @param temperatureMeanC The average temperature.
     * @param sunshineDurationSeconds Total sunshine duration in seconds.
     * @param humidityMeanPercent Average humidity in percent.
     * @param soilMoistureMeanM3M3 Average soil moisture.
     */
    public DailyWeather(LocalDate date, String timezone, Double temperatureMeanC,
                        Double sunshineDurationSeconds, Double humidityMeanPercent,
                        Double soilMoistureMeanM3M3) {
        this(date, timezone, temperatureMeanC, sunshineDurationSeconds, humidityMeanPercent,
                soilMoistureMeanM3M3, null);
    }

    /**
     * Checks if the DaulyWeather instance is valid by ensuring the timezone is correct and
     * that it represents a date before the current day.
     * @throws ResponseStatusException If timezone is invalid or date of instance is before current day.
     */
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
