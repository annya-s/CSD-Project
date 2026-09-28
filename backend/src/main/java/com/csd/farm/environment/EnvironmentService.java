package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import com.csd.farm.crop.DailyWeather;

/** In charge of packaging the returned data from the (multiple) external API calls into EnvironmentReading(s)
 * 
 *  Sends the result back to EnvironmentController
 */
@Service
public class EnvironmentService {
    private final EnvironmentClient envClient;

    public EnvironmentService(EnvironmentClient envClient) {
        this.envClient = envClient;
    }

    public List<EnvironmentReading> getAllReadings(double latitude, double longitude) throws RuntimeException {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude/latitude inputs");
        }

        List<EnvironmentReading> readings = new ArrayList<>();

        OpenMeteoResponse res = envClient.fetchMeteoData(latitude, longitude);

        if (res.hourly.time == null || res.hourly.time.isEmpty()) {
            throw new NoSuchElementException("No hourly data returned from Open-Meteo");
        }

        if (res.daily.time == null || res.daily.time.isEmpty()) {
            throw new NoSuchElementException("No daily data returned from Open-Meteo");
        }
        
        for (int i = 0; i < res.hourly.time.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(res.hourly.time.get(i));
            Double temperature = res.hourly.temperature_2m.get(i);
            Double soilMoisture = res.hourly.soil_moisture_3_to_9cm.get(i);
            Double vapourPressureDeficit = res.hourly.vapour_pressure_deficit.get(i);

            LocalDate date = dateTime.toLocalDate();
            int dateIndex = res.daily.time.indexOf(date.toString());
            Double sunshineDuration = res.daily.sunshine_duration.get(dateIndex);

            readings.add(new EnvironmentReading(
                dateTime, 
                temperature, 
                soilMoisture,
                vapourPressureDeficit,
                date, 
                sunshineDuration)
            );
        }
        return readings;
    }

    public EnvironmentReading getLatestReading(double latitude, double longitude) {
        int currentHour = LocalTime.now().getHour();

        return getAllReadings(latitude, longitude).get(currentHour);
    }

    public DailyWeather getCompletedDay(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid latitude or longitude.");
        }
        DailyEnvironmentResponse response = envClient.fetchCompletedDayData(latitude, longitude);
        return completedDay(response, Instant.now());
    }

    // Kept separate from the HTTP call so dates, units and missing data can be tested.
    DailyWeather completedDay(DailyEnvironmentResponse response, Instant now) {
        if (response == null || response.daily == null || response.daily.time == null
                || response.timezone == null) {
            throw new IllegalStateException("Open-Meteo did not return daily weather.");
        }
        ZoneId zone = ZoneId.of(response.timezone);
        LocalDate yesterday = now.atZone(zone).toLocalDate().minusDays(1);
        int dayIndex = -1;
        for (int i = 0; i < response.daily.time.size(); i++) {
            Long timestamp = response.daily.time.get(i);
            if (timestamp != null && Instant.ofEpochSecond(timestamp).atZone(zone).toLocalDate().equals(yesterday)) {
                dayIndex = i;
                break;
            }
        }
        if (dayIndex < 0) {
            throw new IllegalStateException("Open-Meteo did not return yesterday's weather.");
        }
        requireUnit(response.daily_units, "temperature_2m_mean", "°C");
        requireUnit(response.daily_units, "sunshine_duration", "s");
        requireUnit(response.hourly_units, "relative_humidity_2m", "%");
        requireUnit(response.hourly_units, "soil_moisture_3_to_9cm", "m³/m³");

        Double temperature = validValue(response.daily.temperature_2m_mean, dayIndex, -100, 100);
        Double sunshine = validValue(response.daily.sunshine_duration, dayIndex, 0, 86400);
        long start = yesterday.atStartOfDay(zone).toEpochSecond();
        long end = yesterday.plusDays(1).atStartOfDay(zone).toEpochSecond();
        Double humidity = null;
        Double soilMoisture = null;
        if (response.hourly != null) {
            humidity = completeDayMean(response.hourly.time, response.hourly.relative_humidity_2m, start, end, 100);
            soilMoisture = completeDayMean(response.hourly.time, response.hourly.soil_moisture_3_to_9cm, start, end, 1);
        }
        // Missing values stay null. Soil water stays in m³/m³ in storage.
        return new DailyWeather(yesterday, zone.getId(), temperature, sunshine, humidity, soilMoisture);
    }

    private Double completeDayMean(List<Long> times, List<Double> values, long start, long end, double maximum) {
        if (times == null || values == null || times.size() != values.size()) return null;
        long expectedTime = start;
        double total = 0;
        int count = 0;
        for (int i = 0; i < times.size(); i++) {
            Long time = times.get(i);
            if (time == null) return null;
            if (time < start || time >= end) continue;
            // Require every hour exactly once, including 23/25-hour local days.
            if (time != expectedTime) return null;
            Double value = validValue(values, i, 0, maximum);
            if (value == null) return null;
            total += value;
            count++;
            expectedTime += 3600;
        }
        return expectedTime == end && count > 0 ? total / count : null;
    }

    private Double validValue(List<Double> values, int index, double minimum, double maximum) {
        if (values == null || index >= values.size()) return null;
        Double value = values.get(index);
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum ? value : null;
    }

    private void requireUnit(Map<String, String> units, String variable, String expected) {
        if (units == null || !expected.equals(units.get(variable))) {
            throw new IllegalStateException("Unexpected Open-Meteo unit for " + variable + ".");
        }
    }
}



