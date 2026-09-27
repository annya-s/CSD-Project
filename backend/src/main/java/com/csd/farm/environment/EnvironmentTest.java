package com.csd.farm.environment;

import java.util.List;

public class EnvironmentTest {
    public static void main(String[] args) {
        WeatherClient wClient = new WeatherClient();
        WeatherService wService = new WeatherService(wClient);

        List<EnvironmentReading> allReadings = wService.getAllReadings(52.52, 13.41);
        System.out.println(allReadings);

        EnvironmentReading latestReading = wService.getLatestReading(52.52, 13.41);
        System.out.println(latestReading + "\n");
        
        
    }
}