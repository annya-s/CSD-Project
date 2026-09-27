package com.csd.farm.environment;

import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** In charge of making the (multiple, if applicable) external API calls
 * 
 *  Returns their data to WeatherService
 */
@Component
public class WeatherClient {
    public OpenMeteoResponse fetchMeteoData(double latitude, double longitude) {
        RestClient restClient = RestClient.create("https://api.open-meteo.com");
        String timeZone = ZoneId.systemDefault().toString();

        RestClient.ResponseSpec response = restClient.get().uri(uriBuilder -> uriBuilder
            .path("/v1/forecast")
            .queryParam("latitude", latitude)
            .queryParam("longitude", longitude)
            .queryParam("timezone", timeZone)
            .queryParam("daily", "sunshine_duration")
            .queryParam("hourly", "temperature_2m,soil_moisture_3_to_9cm")
            .build()).retrieve();
        return response.body(OpenMeteoResponse.class);
    }
}