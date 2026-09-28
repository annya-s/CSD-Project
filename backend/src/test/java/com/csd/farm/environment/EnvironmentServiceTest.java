package com.csd.farm.environment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EnvironmentServiceTest {
    private final EnvironmentService service = new EnvironmentService(null);

    @Test
    void usesLocationsYesterdayAndConvertsEveryHourlyValueToDailyMean() {
        var response = response("Asia/Singapore", "2026-09-27");
        // UTC is still September 27, but Singapore has already reached September 28.
        var weather = service.completedDay(response, Instant.parse("2026-09-27T18:00:00Z"));
        assertThat(weather.date()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(weather.temperatureMeanC()).isEqualTo(25.0);
        assertThat(weather.sunshineDurationSeconds()).isEqualTo(25200.0);
        assertThat(weather.humidityMeanPercent()).isEqualTo(70.0);
        assertThat(weather.soilMoistureMeanM3M3()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.000001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29", "2026-10-25"})
    void handlesShortAndLongDaysAtDaylightSavingChanges(String date) {
        var response = response("Europe/Berlin", date);
        Instant nextDay = LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        assertThat(response.hourly.time.size()).isIn(23, 25);
        assertThat(service.completedDay(response, nextDay).humidityMeanPercent()).isEqualTo(70.0);
    }

    @Test
    void missingOrInvalidValuesStayNullRatherThanBecomingZeroOrPartialMeans() {
        var response = response("Asia/Singapore", "2026-09-27");
        response.hourly.soil_moisture_3_to_9cm.set(4, null);
        response.daily.sunshine_duration.set(0, null);
        response.hourly.relative_humidity_2m.set(3, 120.0);
        var weather = service.completedDay(response, Instant.parse("2026-09-28T02:00:00Z"));
        assertThat(weather.sunshineDurationSeconds()).isNull();
        assertThat(weather.soilMoistureMeanM3M3()).isNull();
        assertThat(weather.humidityMeanPercent()).isNull();
        assertThat(weather.temperatureMeanC()).isEqualTo(25.0);
    }

    @Test
    void missingHourPreventsDailyMeanAndMissingYesterdayPreventsAssessment() {
        var response = response("Asia/Singapore", "2026-09-27");
        response.hourly.time.remove(3);
        response.hourly.relative_humidity_2m.remove(3);
        response.hourly.soil_moisture_3_to_9cm.remove(3);
        assertThat(service.completedDay(response, Instant.parse("2026-09-28T02:00:00Z")).humidityMeanPercent()).isNull();
        assertThatThrownBy(() -> service.completedDay(response, Instant.parse("2026-09-30T02:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnexpectedSoilUnitsInsteadOfTreatingPercentAsM3M3() {
        var response = response("Asia/Singapore", "2026-09-27");
        response.hourly_units = Map.of("relative_humidity_2m", "%", "soil_moisture_3_to_9cm", "%");
        assertThatThrownBy(() -> service.completedDay(response, Instant.parse("2026-09-28T02:00:00Z")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("soil_moisture");
    }

    @Test
    void clientRequestsPastDaysLocationTimezoneAndCorrectVariables() {
        var builder = RestClient.builder().baseUrl("https://api.open-meteo.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(queryParam("latitude", "1.3521"))
                .andExpect(queryParam("longitude", "103.8198"))
                .andExpect(queryParam("timezone", "auto"))
                .andExpect(queryParam("past_days", "2"))
                .andExpect(queryParam("timeformat", "unixtime"))
                .andExpect(queryParam("daily", "temperature_2m_mean,sunshine_duration"))
                .andExpect(queryParam("hourly", "relative_humidity_2m,soil_moisture_3_to_9cm"))
                .andRespond(withSuccess("{\"timezone\":\"Asia/Singapore\",\"hourly\":{\"time\":[1790438400]}}", MediaType.APPLICATION_JSON));
        var result = new EnvironmentClient(builder.build()).fetchCompletedDayData(1.3521, 103.8198);
        assertThat(result.hourly.time).containsExactly(1790438400L);
        server.verify();
    }

    private DailyEnvironmentResponse response(String timezone, String date) {
        ZoneId zone = ZoneId.of(timezone);
        LocalDate day = LocalDate.parse(date);
        long start = day.atStartOfDay(zone).toEpochSecond();
        long end = day.plusDays(1).atStartOfDay(zone).toEpochSecond();
        var result = new DailyEnvironmentResponse();
        result.timezone = timezone;
        result.daily_units = Map.of("temperature_2m_mean", "°C", "sunshine_duration", "s");
        result.hourly_units = Map.of("relative_humidity_2m", "%", "soil_moisture_3_to_9cm", "m³/m³");
        result.daily = new DailyEnvironmentResponse.Daily();
        result.daily.time = List.of(start, end);
        result.daily.temperature_2m_mean = new ArrayList<>(List.of(25.0, 99.0));
        result.daily.sunshine_duration = new ArrayList<>(List.of(25200.0, 0.0));
        result.hourly = new DailyEnvironmentResponse.Hourly();
        result.hourly.time = new ArrayList<>();
        result.hourly.relative_humidity_2m = new ArrayList<>();
        result.hourly.soil_moisture_3_to_9cm = new ArrayList<>();
        for (long time = start; time < end; time += 3600) {
            result.hourly.time.add(time);
            result.hourly.relative_humidity_2m.add(70.0);
            result.hourly.soil_moisture_3_to_9cm.add(0.25);
        }
        return result;
    }
}
