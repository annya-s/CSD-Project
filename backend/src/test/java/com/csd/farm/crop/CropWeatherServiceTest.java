
package com.csd.farm.crop;

import java.util.Optional;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CropWeatherServiceTest {

    private CropRepository cropRepository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
            "jdbc:h2:mem:crop-coordinates-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        );

        this.jdbc = JdbcClient.create(dataSource);
        cropRepository = new CropRepository(jdbc);

        jdbc.sql("""
            CREATE TABLE public."FARMS" (
                "Farm_ID" BIGINT PRIMARY KEY,
                "Latitude" DOUBLE PRECISION NOT NULL,
                "Longitude" DOUBLE PRECISION NOT NULL
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE public."CROP_CYCLES" (
                "Crop_Cycle_ID" BIGINT PRIMARY KEY,
                "Farm_ID" BIGINT NOT NULL,
                CONSTRAINT fk_crop_cycle_farm
                    FOREIGN KEY ("Farm_ID")
                    REFERENCES public."FARMS" ("Farm_ID")
            )
            """).update();
    }

    @Test
    void findCoordinatesForFarm_ShouldReturnCoordinatesForMatchingCropCycle() {
        // Arrange
        insertFarm(10L, 1.3521, 103.8198);
        insertCropCycle(100L, 10L);

        // Act
        Optional<CropRepository.FarmCoordinates> result =
            cropRepository.findCoordinatesForFarm(100);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(1.3521, result.get().latitude(), 0.000001);
        assertEquals(103.8198, result.get().longitude(), 0.000001);
    }

    @Test
    void findCoordinatesForFarm_ShouldReturnEmptyWhenCropCycleDoesNotExist() {
        // Act
        Optional<CropRepository.FarmCoordinates> result =
            cropRepository.findCoordinatesForFarm(999);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findCoordinatesForFarm_ShouldReturnCoordinatesFromCorrectFarm() {
        // Arrange
        insertFarm(10L, 1.3521, 103.8198);
        insertFarm(20L, 3.1390, 101.6869);

        insertCropCycle(100L, 10L);
        insertCropCycle(200L, 20L);

        // Act
        CropRepository.FarmCoordinates first =
            cropRepository.findCoordinatesForFarm(100).orElseThrow();

        CropRepository.FarmCoordinates second =
            cropRepository.findCoordinatesForFarm(200).orElseThrow();

        // Assert
        assertEquals(1.3521, first.latitude(), 0.000001);
        assertEquals(103.8198, first.longitude(), 0.000001);

        assertEquals(3.1390, second.latitude(), 0.000001);
        assertEquals(101.6869, second.longitude(), 0.000001);
    }

    private void insertFarm(long farmId, double latitude, double longitude) {
        JdbcClient jdbc = getJdbcClient();

        jdbc.sql("""
            INSERT INTO public."FARMS"
                ("Farm_ID", "Latitude", "Longitude")
            VALUES (:farmId, :latitude, :longitude)
            """)
            .param("farmId", farmId)
            .param("latitude", latitude)
            .param("longitude", longitude)
            .update();
    }

    private void insertCropCycle(long cycleId, long farmId) {
        JdbcClient jdbc = getJdbcClient();

        jdbc.sql("""
            INSERT INTO public."CROP_CYCLES" ("Crop_Cycle_ID", "Farm_ID")
            VALUES (:cycleId, :farmId)
            """)
            .param("cycleId", cycleId)
            .param("farmId", farmId)
            .update();
    }

    private JdbcClient getJdbcClient() {
        return jdbc;
    }
}
