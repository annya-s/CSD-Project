package com.csd.farm.crop;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Represents a repository where each entry represents a day's
 * weather and its conditions.
 */
@Repository
public class CropWeatherRepository {

    private final JdbcClient jdbc;

    /**
     * Singular constructor for a CropWeatherRepository.
     * @param jdbc The JdbcCilent linked to this repository.
     */
    public CropWeatherRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads for a specific daily weather condition for a farmer's crop harvest.
     * @param farmerId The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Data and time when crop was planted.
     * @return A DailyWeather instance representing the weather for the day. May not exist.
     */
    public Optional<DailyWeather> find(Long farmerId, CropType cropType, OffsetDateTime plantedAt) {
        return jdbc.sql("""
                SELECT weather_date, weather_timezone, temperature_mean_c, sunshine_duration_seconds,
                       humidity_mean_percent, soil_moisture_mean_m3m3
                FROM farm.crop_entry
                WHERE farmer_id = :farmerId AND crop_type = :cropType AND planted_at = :plantedAt
                  AND weather_date IS NOT NULL
                """)
                .param("farmerId", farmerId)
                .param("cropType", cropType.name())
                .param("plantedAt", plantedAt)
                .query((row, index) -> new DailyWeather(
                        row.getObject("weather_date", LocalDate.class), row.getString("weather_timezone"),
                        row.getObject("temperature_mean_c", Double.class),
                        row.getObject("sunshine_duration_seconds", Double.class),
                        row.getObject("humidity_mean_percent", Double.class),
                        row.getObject("soil_moisture_mean_m3m3", Double.class)))
                .optional();
    }

    /**
     * Updates and replaces the day's complete snapshot, including nulls. Older days cannot overwrite newer data.
     * @param farmerId The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Date and time when crop was planted.
     * @param weather Daily weather of the day.
     * @return Whether operation is successful.
     */
    public boolean save(Long farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather) {
        return save(farmerId, cropType, plantedAt, weather, "MANUAL");
    }

    /**
     * Updates and replaces the day's complete snapshot using the OpenMeteo API.
     * @param farmerId The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Date and time when crop was planted.
     * @param weather Daily weather of the day.
     * @return Whether operation is successful.
     */
    public boolean saveFromApi(Long farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather) {
        return save(farmerId, cropType, plantedAt, weather, "OPEN_METEO");
    }

    /**
     * Reads from the OpenMeteo API to find the daily weather conditions for a farmer's specifc crop harvest.
     * @param farmerId The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Data and time when crop was planted.
     * @return A DailyWeather instance representing the weather for the day. May not exist.
     */
    public Optional<DailyWeather> findFromApi(Long farmerId, CropType cropType, OffsetDateTime plantedAt) {
        String source = jdbc.sql("""
                SELECT weather_source FROM farm.crop_entry
                WHERE farmer_id = :farmerId AND crop_type = :cropType AND planted_at = :plantedAt
                """)
                .param("farmerId", farmerId).param("cropType", cropType.name()).param("plantedAt", plantedAt)
                .query(String.class).optional().orElse("");
        return source.equals("OPEN_METEO") ? find(farmerId, cropType, plantedAt) : Optional.empty();
    }

    /**
     * Updates the repository for a specific entry.
     * @param farmerId The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Date and time when crop was planted.
     * @param weather Daily weather of the day.
     * @param source String representing where the source of the weather conditions is from.
     * @return Whether operation was successful.
     */
    private boolean save(Long farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather, String source) {
        return jdbc.sql("""
                UPDATE farm.crop_entry
                SET weather_date = :date, weather_timezone = :timezone,
                    temperature_mean_c = :temperature, sunshine_duration_seconds = :sunshine,
                    humidity_mean_percent = :humidity, soil_moisture_mean_m3m3 = :soilMoisture,
                    weather_source = :source
                WHERE farmer_id = :farmerId AND crop_type = :cropType AND planted_at = :plantedAt
                  AND (weather_date IS NULL OR weather_date <= :date
                       OR (:source = 'OPEN_METEO' AND weather_source = 'MANUAL'))
                """)
                .param("date", weather.date())
                .param("source", source)
                .param("timezone", weather.timezone())
                .param("temperature", weather.temperatureMeanC(), Types.DOUBLE)
                .param("sunshine", weather.sunshineDurationSeconds(), Types.DOUBLE)
                .param("humidity", weather.humidityMeanPercent(), Types.DOUBLE)
                .param("soilMoisture", weather.soilMoistureMeanM3M3(), Types.DOUBLE)
                .param("farmerId", farmerId)
                .param("cropType", cropType.name())
                .param("plantedAt", plantedAt)
                .update() == 1;
    }
}
