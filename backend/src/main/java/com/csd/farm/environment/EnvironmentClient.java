package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** In charge of making the external API calls and returning their data to EnvironmentService. */
@Component
public class EnvironmentClient {
    private final RestClient restClient;

    public EnvironmentClient() {
        this.restClient = RestClient.create();
    }

    public OpenMeteoResponse fetchLatestMeteoData(double latitude, double longitude) {
        String timeZone = ZoneId.systemDefault().toString();

        RestClient.ResponseSpec response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("api.open-meteo.com")
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("timezone", timeZone)
                .queryParam("daily", "sunshine_duration")
                .queryParam("hourly", "temperature_2m,soil_moisture_3_to_9cm,vapour_pressure_deficit")
                .build())
            .retrieve();

        return response.body(OpenMeteoResponse.class);
    }

    public OpenMeteoResponse fetchHistoricalMeteoData(double latitude, double longitude) {
        ZoneId timeZone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(timeZone);
        LocalDate startDate = today.minusDays(14);
        LocalDate endDate = today.minusDays(1);

        RestClient.ResponseSpec response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("historical-forecast-api.open-meteo.com")
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .queryParam("timezone", timeZone.toString())
                .queryParam("daily", "sunshine_duration")
                .queryParam("hourly", "temperature_2m,soil_moisture_0_to_7cm,vapour_pressure_deficit")
                .build())
            .retrieve();

        return response.body(OpenMeteoResponse.class);
    }
}