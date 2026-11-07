
package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(EnvironmentClient.class)
class EnvironmentClientTest {

    @Autowired
    private EnvironmentClient envClient;

    @Autowired
    private MockRestServiceServer server;

    private static final double LATITUDE = 1.35;
    private static final double LONGITUDE = 103.82;

    private static final String MOCK_RESPONSE = """
        {
            "hourly": {
                "time": ["2026-10-21T00:00"],
                "temperature": [27.5],
                "soil_moisture": [0.25],
                "vapour_pressure_deficit": [1.2]
            },
            "daily": {
                "time": ["2026-10-21"],
                "sunshine_duration": [18000.0]
            }
        }
        """;

    @Test
    void fetchLatestMeteoData_ShouldCallForecastEndpoint() {
        // Arrange
        String timeZone = ZoneId.systemDefault().toString();

        server.expect(requestTo(
                containsString(
                    "https://api.open-meteo.com/v1/forecast"
                )
            ))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("latitude", equalTo("1.35")))
            .andExpect(queryParam("longitude", equalTo("103.82")))
            .andExpect(queryParam("timezone", equalTo(timeZone)))
            .andExpect(queryParam(
                "daily", equalTo("sunshine_duration")
            ))
            .andExpect(queryParam(
                "hourly",
                equalTo(
                    "temperature_2m,soil_moisture_3_to_9cm,vapour_pressure_deficit"
                )
            ))
            .andRespond(withSuccess(
                MOCK_RESPONSE,
                MediaType.APPLICATION_JSON
            ));

        // Act
        OpenMeteoResponse result =
            envClient.fetchLatestMeteoData(LATITUDE, LONGITUDE);

        // Assert
        assertNotNull(result);
        assertNotNull(result.hourly);
        assertNotNull(result.daily);

        assertEquals(27.5, result.hourly.temperature.get(0));
        assertEquals(18000.0, result.daily.sunshine_duration.get(0));

        server.verify();
    }

    @Test
    void fetchHistoricalMeteoData_ShouldCallHistoricalEndpointWithLast14Days() {
        // Arrange
        ZoneId timeZone = ZoneId.systemDefault();

        LocalDate today = LocalDate.now(timeZone);
        LocalDate expectedStartDate = today.minusDays(14);
        LocalDate expectedEndDate = today.minusDays(1);

        server.expect(requestTo(
                containsString(
                    "https://historical-forecast-api.open-meteo.com/v1/forecast"
                )
            ))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("latitude", equalTo("1.35")))
            .andExpect(queryParam("longitude", equalTo("103.82")))
            .andExpect(queryParam(
                "start_date", equalTo(expectedStartDate.toString())
            ))
            .andExpect(queryParam(
                "end_date", equalTo(expectedEndDate.toString())
            ))
            .andExpect(queryParam(
                "timezone", equalTo(timeZone.toString())
            ))
            .andExpect(queryParam(
                "daily", equalTo("sunshine_duration")
            ))
            .andExpect(queryParam(
                "hourly",
                equalTo(
                    "temperature_2m,soil_moisture_0_to_7cm,vapour_pressure_deficit"
                )
            ))
            .andRespond(withSuccess(
                MOCK_RESPONSE,
                MediaType.APPLICATION_JSON
            ));

        // Act
        OpenMeteoResponse result =
            envClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE);

        // Assert
        assertNotNull(result);
        assertNotNull(result.hourly);
        assertNotNull(result.daily);

        assertEquals(27.5, result.hourly.temperature.get(0));
        assertEquals(0.25, result.hourly.soil_moisture.get(0));
        assertEquals(1.2, result.hourly.vapour_pressure_deficit.get(0));
        assertEquals(18000.0, result.daily.sunshine_duration.get(0));

        server.verify();
    }
}
