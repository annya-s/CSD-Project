package com.csd.farm.environment;

import java.time.ZoneId;
import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** In charge of making the (multiple, if applicable) external API calls
 * 
 *  Returns their data to EnvironmentService
 */
@Component
public class EnvironmentClient {
    private final RestClient restClient;

    public EnvironmentClient() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl("https://api.open-meteo.com")
                .requestFactory(requests).build();
    }

    // Allows an HTTP stub in tests; production always uses the Open-Meteo URL above.
    EnvironmentClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public OpenMeteoResponse fetchMeteoData(double latitude, double longitude) {
        String timeZone = ZoneId.systemDefault().toString();

        RestClient.ResponseSpec response = restClient.get().uri(uriBuilder -> uriBuilder
            .path("/v1/forecast")
            .queryParam("latitude", latitude)
            .queryParam("longitude", longitude)
            .queryParam("timezone", timeZone)
            .queryParam("daily", "sunshine_duration")
            .queryParam("hourly", "temperature_2m,soil_moisture_3_to_9cm,vapour_pressure_deficit")
            .build()).retrieve();
        return response.body(OpenMeteoResponse.class);
    }

    public DailyEnvironmentResponse fetchCompletedDayData(double latitude, double longitude) {
        return restClient.get().uri(uriBuilder -> uriBuilder
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("timezone", "auto")
                .queryParam("timeformat", "unixtime")
                .queryParam("temperature_unit", "celsius")
                .queryParam("past_days", 2)
                .queryParam("forecast_days", 1)
                .queryParam("daily", "temperature_2m_mean,sunshine_duration")
                .queryParam("hourly", "relative_humidity_2m,soil_moisture_3_to_9cm")
                .build()).retrieve().body(DailyEnvironmentResponse.class);
    }
}
