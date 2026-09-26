package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;


@Service
public class WeatherService {
    private final WeatherClient weatherClient;

    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public WeatherClient.OpenMeteoResponse fetchData(double latitude, double longitude) {
        return weatherClient.fetchMeteoData(latitude, longitude);
    }

    public EnvironmentReading getLatestReading(WeatherClient.OpenMeteoResponse res) {
        LocalTime currentTime = LocalTime.now(ZoneOffset.UTC);
        int currentHour = currentTime.getHour();
        
        List<String> hourlyTimes = res.hourly.time;
        List<Double> temperatures = res.hourly.temperature_2m;
        List<Double> soilMoistures = res.hourly.soil_moisture_3_to_9cm;

        List<String> dailyTimes = res.daily.time;
        List<Double> dailySunshineDuration = res.daily.sunshine_duration;

        if (hourlyTimes == null || hourlyTimes.isEmpty()) {
            throw new NoSuchElementException("No hourly data returned from Open-Meteo");
        }

        if (dailyTimes == null || dailyTimes.isEmpty()) {
            throw new NoSuchElementException("No daily data returned from Open-Meteo");
        }

        return new EnvironmentReading(
            LocalDateTime.parse(hourlyTimes.get(currentHour)),
            temperatures.get(currentHour),
            soilMoistures.get(currentHour),
            LocalDate.parse(dailyTimes.get(0)),
            dailySunshineDuration.get(0)
        );
    }
}



