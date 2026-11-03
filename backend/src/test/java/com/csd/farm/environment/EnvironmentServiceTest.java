package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    private static final double LATITUDE = 1.35;
    private static final double LONGITUDE = 103.82;

    @Mock
    private EnvironmentClient environmentClient;

    @InjectMocks
    private EnvironmentService environmentService;

    // GetLatestReading test set
    @Test
    void getAllReadings_ShouldThrowWhenHourlyBlockIsMissing() {
        // Arrange
        OpenMeteoResponse apiResponse = completeResponse(
            LocalDate.now(ZoneId.systemDefault())
        );
        apiResponse.hourly = null;

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

        // Act & Assert
        assertThrows(NoSuchElementException.class,
            () -> environmentService.getAllReadings(LATITUDE, LONGITUDE));
    }

    @Test
    void getAllReadings_ShouldThrowWhenDailyBlockIsMissing() {
        // Arrange
        OpenMeteoResponse apiResponse = completeResponse(
            LocalDate.now(ZoneId.systemDefault())
        );
        apiResponse.daily = null;

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

        // Act & Assert
        assertThrows(NoSuchElementException.class,
            () -> environmentService.getAllReadings(LATITUDE, LONGITUDE));
    }

    @Test
    void getAllReadings_ShouldIncludeCurrentDayAndForecastReadings() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate tomorrow = today.plusDays(1);

        OpenMeteoResponse apiResponse = completeResponse(today);

        apiResponse.hourly.time = new ArrayList<>(List.of(
            today + "T10:00",
            today + "T11:00",
            tomorrow + "T10:00"
        ));

        apiResponse.hourly.temperature = new ArrayList<>(List.of(25.0, 26.0, 27.0));
        apiResponse.hourly.soil_moisture = new ArrayList<>(List.of(0.2, 0.3, 0.4));
        apiResponse.hourly.vapour_pressure_deficit = new ArrayList<>(List.of(1.0, 1.1, 1.2));
        apiResponse.daily.time = new ArrayList<>(List.of(today.toString(), tomorrow.toString()));
        apiResponse.daily.sunshine_duration = new ArrayList<>(List.of(3600.0, 7200.0));

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getAllReadings(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(3, readings.size());

        assertEquals(today, readings.get(0).date());
        assertEquals(today, readings.get(1).date());
        assertEquals(tomorrow, readings.get(2).date());
    }

    @Test
    void getAllReadings_ShouldReturnNullWhenMeasurementArraysAreShorterThanTimeArrays() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        OpenMeteoResponse apiResponse = completeResponse(today);

        apiResponse.hourly.temperature.remove(335);

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getAllReadings(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(336, readings.size());
        assertNull(readings.get(335).temperature());
    }

    @Test
    void getAllReadings_ShouldKeepIndividualMissingObservationsAsNull() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        OpenMeteoResponse apiResponse = completeResponse(today);

        apiResponse.hourly.temperature.set(100, null);

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getAllReadings(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(336, readings.size());
        assertNull(readings.get(100).temperature());
    }

    @Test
    void getAllReadings_ShouldSkipMissingHourlyTimestamps() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        OpenMeteoResponse apiResponse = completeResponse(today);

        int missingIndex = 100;
        LocalDateTime expectedNextDateTime = LocalDateTime.parse(apiResponse.hourly.time.get(missingIndex + 1));

        apiResponse.hourly.time.remove(missingIndex);
        apiResponse.hourly.temperature.remove(missingIndex);
        apiResponse.hourly.soil_moisture.remove(missingIndex);
        apiResponse.hourly.vapour_pressure_deficit.remove(missingIndex);

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

		// Act
		List<EnvironmentReading> readings =
			environmentService.getAllReadings(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(335, readings.size());
        assertEquals(expectedNextDateTime, readings.get(missingIndex).dateTime());
    }

    @Test
    void getAllReadings_ShouldMapSunshineDurationToCorrectDate() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        OpenMeteoResponse apiResponse = completeResponse(today);

        apiResponse.daily.sunshine_duration.set(0, 3600.0);
        apiResponse.daily.sunshine_duration.set(1, 7200.0);

        when(environmentClient.fetchLatestMeteoData(LATITUDE, LONGITUDE))
            .thenReturn(apiResponse);

		// Act
		List<EnvironmentReading> readings =
			environmentService.getAllReadings(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(3600.0, readings.get(0).sunshineDuration());
        assertEquals(3600.0, readings.get(23).sunshineDuration());

        assertEquals(7200.0, readings.get(24).sunshineDuration());
        assertEquals(7200.0, readings.get(47).sunshineDuration());
    }


    // GetHistoricalReading test set
    @Test
    void getHistoricalReading_ShouldReturn14CompleteDaysOfHourlyReadings() {
        // Arrange
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate startDate = today.minusDays(14);
        LocalDate endDate = today.minusDays(1);
        OpenMeteoResponse apiResponse = completeResponse(startDate);

        when(environmentClient.fetchHistoricalMeteoData(
            LATITUDE, LONGITUDE
        )).thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getHistoricalReading(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(14 * 24, readings.size());
        assertAll(
            () -> assertEquals(startDate.atStartOfDay(), readings.get(0).dateTime()),
            () -> assertEquals(endDate.atTime(23, 0),
                readings.get(readings.size() - 1).dateTime()),
            () -> assertEquals(20.0, readings.get(0).temperature()),
            () -> assertEquals(0.20, readings.get(0).soilMoisture()),
            () -> assertEquals(1.0, readings.get(0).vapourPressureDeficit()),
            () -> assertEquals(1000.0, readings.get(0).sunshineDuration())
        );
        
        verify(environmentClient).fetchHistoricalMeteoData(LATITUDE, LONGITUDE);
    }

    @Test
    void getHistoricalReading_ShouldKeepMissingVariablesAsNull() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);
        LocalDate endDate = startDate.plusDays(13);
        OpenMeteoResponse apiResponse = completeResponse(startDate);
        apiResponse.hourly.temperature = null;
        apiResponse.hourly.soil_moisture = null;
        apiResponse.hourly.vapour_pressure_deficit = null;
        apiResponse.daily.sunshine_duration = null;

        when(environmentClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE)).thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getHistoricalReading(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(336, readings.size());
        assertAll(
            () -> assertNull(readings.get(0).temperature()),
            () -> assertNull(readings.get(0).soilMoisture()),
            () -> assertNull(readings.get(0).vapourPressureDeficit()),
            () -> assertNull(readings.get(0).sunshineDuration())
        );
    }

    @Test
    void getHistoricalReading_ShouldKeepIndividualMissingObservationsAsNull() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);
        OpenMeteoResponse apiResponse = completeResponse(startDate);

        apiResponse.hourly.temperature.set(100, null); // Simulate null data

        when(environmentClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE)).thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings = environmentService.getHistoricalReading(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(336, readings.size());

        assertNull(readings.get(100).temperature());

        assertNotNull(readings.get(99).temperature());
        assertNotNull(readings.get(101).temperature());
    }

    @Test
    void getHistoricalReading_ShouldPreserveTimelineWhenAnHourlyTimestampIsMissing() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);
        OpenMeteoResponse apiResponse = completeResponse(startDate);

        int missingHour = 50;

        apiResponse.hourly.time.remove(missingHour);
        apiResponse.hourly.temperature.remove(missingHour);
        apiResponse.hourly.soil_moisture.remove(missingHour);
        apiResponse.hourly.vapour_pressure_deficit.remove(missingHour);

        when(environmentClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE)).thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getHistoricalReading(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(335, readings.size());
        
        assertEquals(
            LocalDateTime.parse(apiResponse.hourly.time.get(missingHour)),
            readings.get(missingHour).dateTime()
        );

        assertEquals(
            apiResponse.hourly.temperature.get(missingHour),
            readings.get(missingHour).temperature()
        );
    }

    @Test
    void getHistoricalReading_ShouldReturnNullWhenMeasurementArraysAreShorterThanTimeArrays() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);
        LocalDate endDate = startDate.plusDays(13);
        OpenMeteoResponse apiResponse = completeResponse(startDate);
        apiResponse.hourly.temperature.remove(335);
        apiResponse.daily.sunshine_duration.remove(13);

        when(environmentClient.fetchHistoricalMeteoData(
            LATITUDE, LONGITUDE
        )).thenReturn(apiResponse);

        // Act
        List<EnvironmentReading> readings =
            environmentService.getHistoricalReading(LATITUDE, LONGITUDE);

        // Assert
        assertEquals(336, readings.size());
        assertAll(
            () -> assertNull(readings.get(335).temperature()),
            () -> assertNull(readings.get(13 * 24).sunshineDuration()),
            () -> assertNull(readings.get(13 * 24 + 23).sunshineDuration()),
            () -> assertEquals(20.0 + 334, readings.get(334).temperature())
        );
    }

    @Test
    void getHistoricalReading_ShouldThrowWhenDailyBlockIsMissing() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);
        OpenMeteoResponse apiResponse = completeResponse(startDate);
        apiResponse.daily = null;

        when(environmentClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE)).thenReturn(apiResponse);

        // Act & Assert
        assertThrows(NoSuchElementException.class,
            () -> environmentService.getHistoricalReading(LATITUDE, LONGITUDE));
    }

    @Test
    void getHistoricalReading_ShouldRejectInvalidCoordinates() {
        // Arrange
        double invalidLatitude = 91.0;

        // Act + Assert
        assertThrows(IllegalArgumentException.class,
            () -> environmentService.getHistoricalReading(invalidLatitude, LONGITUDE));
    }

    private OpenMeteoResponse completeResponse(LocalDate startDate) {
        OpenMeteoResponse response = new OpenMeteoResponse();
        response.hourly = new OpenMeteoResponse.HourlyBlock();
        response.daily = new OpenMeteoResponse.DailyBlock();

        response.hourly.time = new ArrayList<>();
        response.hourly.temperature = new ArrayList<>();
        response.hourly.soil_moisture = new ArrayList<>();
        response.hourly.vapour_pressure_deficit = new ArrayList<>();

        for (int i = 0; i < 336; i++) {
            LocalDateTime dateTime = startDate.atStartOfDay().plusHours(i);
            response.hourly.time.add(dateTime.toString());
            response.hourly.temperature.add(20.0 + i);
            response.hourly.soil_moisture.add(0.20 + i / 1000.0);
            response.hourly.vapour_pressure_deficit.add(1.0 + i / 100.0);
        }

        response.daily.time = new ArrayList<>();
        response.daily.sunshine_duration = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            response.daily.time.add(startDate.plusDays(i).toString());
            response.daily.sunshine_duration.add(1000.0 + i);
        }

        return response;
    }

    @Test
    void getHistoricalReading_ShouldThrowWhenHourlyBlockIsMissing() {
        // Arrange
        LocalDate startDate = LocalDate.now(ZoneId.systemDefault()).minusDays(14);

        OpenMeteoResponse apiResponse = completeResponse(startDate);
        apiResponse.hourly = null;

        when(environmentClient.fetchHistoricalMeteoData(LATITUDE, LONGITUDE)).thenReturn(apiResponse);

        // Act & Assert
        assertThrows(NoSuchElementException.class, 
            () -> environmentService.getHistoricalReading(LATITUDE, LONGITUDE));
    }
}
