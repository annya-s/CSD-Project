package com.csd.farm.environment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherController {
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/api/environment/latest")
    public EnvironmentReading getLatestEnviornmentReading(@RequestParam double latitude, @RequestParam double longitude) {
        return weatherService.getLatestReading(weatherService.fetchData(latitude, longitude));
    }
}

