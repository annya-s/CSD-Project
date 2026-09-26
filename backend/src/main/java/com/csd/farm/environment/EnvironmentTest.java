package com.csd.farm.environment;

public class EnvironmentTest {
    public static void main(String[] args) {
        WeatherClient wClient = new WeatherClient();
        WeatherService wService = new WeatherService(wClient);

        EnvironmentReading reading = wService.getLatestReading(wService.fetchData(52.52, 13.41));
        System.out.println(reading);
    }
}