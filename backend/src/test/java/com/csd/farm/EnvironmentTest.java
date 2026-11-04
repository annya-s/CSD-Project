package com.csd.farm;

import java.util.List;

import org.springframework.web.client.RestClient;

import com.csd.farm.environment.EnvironmentClient;
import com.csd.farm.environment.EnvironmentReading;
import com.csd.farm.environment.EnvironmentService;

public class EnvironmentTest {
    public static void main(String[] args) {
        EnvironmentClient envClient = new EnvironmentClient(RestClient.builder());
        EnvironmentService envService = new EnvironmentService(envClient);

        List<EnvironmentReading> allReadings = envService.getAllReadings(52.52, 13.41);
        System.out.println(allReadings);

        EnvironmentReading latestReading = envService.getLatestReading(52.52, 13.41);
        System.out.println(latestReading);
    }
}