package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

/** In charge of packaging the returned data from the (multiple) external API calls into EnvironmentReading(s)
 * 
 *  Sends the result back to WeatherController
 */
@Service
public class WeatherService {
    private final WeatherClient weatherClient;

    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public List<EnvironmentReading> getAllReadings(double latitude, double longitude) throws NoSuchElementException {
        List<EnvironmentReading> readings = new ArrayList<>();

        OpenMeteoResponse res = weatherClient.fetchMeteoData(latitude, longitude);

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
}



