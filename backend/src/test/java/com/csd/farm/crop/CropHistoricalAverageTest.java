package com.csd.farm.crop;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.csd.farm.environment.HistoricalReadingAverage;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class CropHistoricalAverageTest {

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcClient jdbc;
    private CropRepository cropRepository;

    @BeforeEach
    void setUp() {
        cropRepository = new CropRepository(jdbc);
        // Use the same disposable PostgreSQL schema as CI; never the live database.
        jdbc.sql("DELETE FROM public.\"USERS\"").update();
        jdbc.sql("""
            INSERT INTO public."USERS" ("User_ID", "User_Name", "User_Email", "Password_Hash")
            VALUES (1, 'history_test', 'history@example.com', 'unused-test-hash')
            """).update();
        jdbc.sql("""
            INSERT INTO public."FARMS" ("Farm_ID", "User_ID", "Farm_Name", "Latitude", "Longitude")
            VALUES (1, 1, 'Historical test farm', 1.3521, 103.8198)
            """).update();
        jdbc.sql("""
            INSERT INTO public."CROP_CYCLES" ("Crop_Cycle_ID", "Farm_ID", "Crop_Type_ID", "Date_Planted")
            SELECT cycle_id, 1, "Crop_Type_ID", DATE '2026-10-01' + cycle_id
            FROM public."CROP_TYPES" CROSS JOIN (VALUES (1), (2)) cycles(cycle_id)
            WHERE "Crop_Name"='Potato'
            """).update();
    }

    @Test
    void saveHistoricalAverageReading_ShouldInsertNewRow() {
        // Arrange
        HistoricalReadingAverage average = new HistoricalReadingAverage(
            1,
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 7),
            23.5,
            0.32,
            1.4,
            7200.0
        );

        // Act
        cropRepository.saveHistoricalAverageReading(average);

        // Assert
        HistoricalReadingAverage saved = findAverage(1);

        assertAll(
            () -> assertEquals(1, saved.cropCycleId()),
            () -> assertEquals(average.startDate(), saved.startDate()),
            () -> assertEquals(average.endDate(), saved.endDate()),
            () -> assertEquals(23.5, saved.temperatureMean(), 0.000001),
            () -> assertEquals(0.32, saved.soilMoistureMean(), 0.000001),
            () -> assertEquals(1.4, saved.vpdMean(), 0.000001),
            () -> assertEquals(7200.0, saved.sunshineDurationMean(), 0.000001),
            () -> assertEquals(1, countReadings())
        );
    }

    @Test
    void saveHistoricalAverageReading_ShouldUpdateExistingRow() {
        // Arrange
        HistoricalReadingAverage original = new HistoricalReadingAverage(
            1,
            LocalDate.of(2026, 10, 11),
            LocalDate.of(2026, 10, 24),
            22.0,
            0.30,
            1.2,
            6000.0
        );

        HistoricalReadingAverage updated = new HistoricalReadingAverage(
            1,
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 7),
            25.0,
            0.40,
            2.0,
            8000.0
        );

        cropRepository.saveHistoricalAverageReading(original);

        // Act
        cropRepository.saveHistoricalAverageReading(updated);

        // Assert
        HistoricalReadingAverage saved = findAverage(1);

        assertAll(
            () -> assertEquals(updated.startDate(), saved.startDate()),
            () -> assertEquals(updated.endDate(), saved.endDate()),
            () -> assertEquals(25.0, saved.temperatureMean(), 0.000001),
            () -> assertEquals(0.40, saved.soilMoistureMean(), 0.000001),
            () -> assertEquals(2.0, saved.vpdMean(), 0.000001),
            () -> assertEquals(8000.0, saved.sunshineDurationMean(), 0.000001),
            () -> assertEquals(1, countReadings())
        );
    }

    @Test
    void saveHistoricalAverageReading_ShouldKeepDifferentCropCyclesSeparate() {
        // Arrange
        HistoricalReadingAverage first = new HistoricalReadingAverage(
            1,
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 7),
            23.0,
            0.30,
            1.5,
            7000.0
        );

        HistoricalReadingAverage second = new HistoricalReadingAverage(
            2,
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 7),
            28.0,
            0.45,
            2.5,
            8500.0
        );

        // Act
        cropRepository.saveHistoricalAverageReading(first);
        cropRepository.saveHistoricalAverageReading(second);

        // Assert
        HistoricalReadingAverage savedFirst = findAverage(1);
        HistoricalReadingAverage savedSecond = findAverage(2);

        assertAll(
            () -> assertEquals(23.0, savedFirst.temperatureMean(), 0.000001),
            () -> assertEquals(28.0, savedSecond.temperatureMean(), 0.000001),
            () -> assertEquals(2, countReadings())
        );
    }

    @Test
    void saveHistoricalAverageReading_ShouldAllowNullMeasurements() {
        // Arrange
        HistoricalReadingAverage average = new HistoricalReadingAverage(
            1,
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 7),
            null,
            0.35,
            null,
            7200.0
        );

        // Act
        cropRepository.saveHistoricalAverageReading(average);

        // Assert
        HistoricalReadingAverage saved = findAverage(1);

        assertAll(
            () -> assertNull(saved.temperatureMean()),
            () -> assertEquals(0.35, saved.soilMoistureMean(), 0.000001),
            () -> assertNull(saved.vpdMean()),
            () -> assertEquals(7200.0, saved.sunshineDurationMean(), 0.000001)
        );
    }

    private HistoricalReadingAverage findAverage(int cropCycleId) {
        return jdbc.sql("""
            SELECT
                "Crop_Cycle_ID",
                "Start_Date",
                "End_Date",
                "Temperature_Mean",
                "Soil_Moisture_Mean",
                "Vapour_Pressure_Deficit_Mean",
                "Sunshine_Duration_Mean_Seconds"
            FROM public."CROP_WEATHER_HISTORICAL_AVERAGE"
            WHERE "Crop_Cycle_ID" = :cropCycleId
            """)
            .param("cropCycleId", cropCycleId)
            .query((rs, rowNum) -> new HistoricalReadingAverage(
                rs.getInt("Crop_Cycle_ID"),
                rs.getDate("Start_Date").toLocalDate(),
                rs.getDate("End_Date").toLocalDate(),
                rs.getObject("Temperature_Mean", Double.class),
                rs.getObject("Soil_Moisture_Mean", Double.class),
                rs.getObject("Vapour_Pressure_Deficit_Mean", Double.class),
                rs.getObject("Sunshine_Duration_Mean_Seconds", Double.class)
            ))
            .single();
    }

    private long countReadings() {
        return jdbc.sql("""
            SELECT COUNT(*)
            FROM public."CROP_WEATHER_HISTORICAL_AVERAGE"
            """)
            .query(Long.class)
            .single();
    }
}