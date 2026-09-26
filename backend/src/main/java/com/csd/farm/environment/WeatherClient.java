package com.csd.farm.environment;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@Component
public class WeatherClient {
    private final RestClient restClient = RestClient.create("https://api.open-meteo.com");

    public OpenMeteoResponse fetchMeteoData(double latitude, double longitude) {
        RestClient.ResponseSpec response = restClient.get().uri(uriBuilder -> uriBuilder
            .path("/v1/forecast")
            .queryParam("latitude", latitude)
            .queryParam("longitude", longitude)
            .queryParam("daily", "sunshine_duration")
            .queryParam("hourly", "temperature_2m,soil_moisture_3_to_9cm")
            .build()).retrieve();
        return response.body(OpenMeteoResponse.class);
    }

    public String fetchRawForDebugging(double latitude, double longitude) {
    return restClient.get().uri(uriBuilder -> uriBuilder
        .path("/v1/forecast")
        .queryParam("latitude", latitude)
        .queryParam("longitude", longitude)
        .queryParam("daily", "sunshine_duration")
        .queryParam("hourly", "temperature_2m,soil_moisture_3_to_9cm")
        .build())
        .retrieve()
        .body(String.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenMeteoResponse {
        public HourlyBlock hourly;
        public DailyBlock daily;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class HourlyBlock {
            public List<String> time;
            public List<Double> temperature_2m;
            public List<Double> soil_moisture_3_to_9cm;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class DailyBlock {
            public List<String> time;
            public List<Double> sunshine_duration;
        }
    }

}