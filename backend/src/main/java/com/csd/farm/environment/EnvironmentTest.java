package com.csd.farm.environment;

import java.util.List;

public class EnvironmentTest {
    public static void main(String[] args) {
        EnvironmentClient envClient = new EnvironmentClient();
        EnvironmentService envService = new EnvironmentService(envClient);

        List<EnvironmentReading> allReadings = envService.getAllReadings(52.52, 13.41);
        System.out.println(allReadings);

        EnvironmentReading latestReading = envService.getLatestReading(52.52, 13.41);
        System.out.println(latestReading);
    }
}