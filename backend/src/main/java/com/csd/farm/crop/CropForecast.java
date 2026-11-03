package com.csd.farm.crop;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import com.csd.farm.environment.EnvironmentReading;

/**
 * Represents a forecast of weather readings using a crop's specific area as a timezone.
 * 
 * Adapts the environment team's hourly data without changing their API code.
 */ 
final class CropForecast {

    /**
     * Default constructor.
     */
    private CropForecast() { }

    /**
     * Converts a set of environment readings for a time zone into a DailyWeather instance.
     * 
     * @param readings The list of environment readings.
     * @param day The day of the readings.
     * @param zone The time zone.
     * @return A DailyWeather instance representing the weather conditions for the day.
     * @throws IllegalStateException When readings are unavailable or missing.
     */
    static DailyWeather forDay(List<EnvironmentReading> readings, LocalDate day, ZoneId zone) {
        if (readings == null) {
            throw new IllegalStateException("No weather forecast was returned.");
        }
        List<EnvironmentReading> today = readings.stream()
                .filter(reading -> reading != null && day.equals(reading.date()))
                .toList();
        if (today.isEmpty()) {
            throw new IllegalStateException("Today's weather forecast is unavailable.");
        }

        boolean complete = hasEveryHour(today, day, zone);
        Double temperature = complete ? mean(today, EnvironmentReading::temperature, -100, 100) : null;
        Double soilMoisture = complete ? mean(today, EnvironmentReading::soilMoisture, 0, 1) : null;
        Double vapourPressureDeficit = complete
                ? mean(today, EnvironmentReading::vapourPressureDeficit, 0, Double.MAX_VALUE) : null;

        // Each hourly reading repeats the same daily total. Never add these totals together.
        Double sunshine = today.get(0).sunshineDuration();
        if (!valid(sunshine, 0, 86400)
                || today.stream().anyMatch(reading -> !sunshine.equals(reading.sunshineDuration()))) {
            return new DailyWeather(day, zone.getId(), temperature, null, null, soilMoisture,
                    vapourPressureDeficit);
        }
        // Humidity is not requested by the environment API. Keep its legacy storage field empty.
        return new DailyWeather(day, zone.getId(), temperature, sunshine, null, soilMoisture,
                vapourPressureDeficit);
    }

    /**
     * Checks if the readings represent all hours in the day. Returns false if not enough readings
     * or if readings do not match the hours of the day.
     * 
     * @param readings The list of environment readings.
     * @param day The day of the readings.
     * @param zone The time zone.
     * @return Whether it correctly rperesents every hour.
     */
    private static boolean hasEveryHour(List<EnvironmentReading> readings, LocalDate day, ZoneId zone) {
        long hoursInDay = Duration.between(day.atStartOfDay(zone), day.plusDays(1).atStartOfDay(zone)).toHours();
        if (readings.size() != hoursInDay) return false;

        var times = new HashSet<java.time.LocalDateTime>();
        for (EnvironmentReading reading : readings) {
            var time = reading.dateTime();
            if (time == null || !time.toLocalDate().equals(day)
                    || time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0
                    || zone.getRules().getValidOffsets(time).size() != 1 || !times.add(time)) {
                // The teammate's local timestamps cannot distinguish a repeated daylight-saving hour.
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the mean of a list of environment readings' values.
     * Returns null if any reading or the mean itself is not valid.
     * 
     * @param readings The list of environment readings.
     * @param valueOf The function that assigns a value to an enviornment reading.
     * @param minimum The lower bound.
     * @param maximum The upper bound.
     * @return The mean of all environment readings' values.
     */
    private static Double mean(List<EnvironmentReading> readings,
                               Function<EnvironmentReading, Double> valueOf,
                               double minimum, double maximum) {
        double total = 0;
        for (EnvironmentReading reading : readings) {
            Double value = valueOf.apply(reading);
            if (!valid(value, minimum, maximum)) return null;
            total += value;
        }
        double average = total / readings.size();
        return Double.isFinite(average) ? average : null;
    }

    /**
     * Checks if a value is valid, meaning it falls within the bound and is a finite floating value.
     * @param value The value.
     * @param minimum The lower bound.
     * @param maximum The upper bound.
     * @return Whether the valud is valid.
     */
    private static boolean valid(Double value, double minimum, double maximum) {
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
