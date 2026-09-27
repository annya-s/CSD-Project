package com.csd.farm.crop;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CropWeatherRepository {

    private final JdbcClient jdbc;

    public CropWeatherRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DailyWeather> find(UUID farmerId, CropType cropType, OffsetDateTime plantedAt) {
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

    // Replace the day's complete snapshot, including nulls. Older days cannot overwrite newer data.
    public boolean save(UUID farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather) {
        return save(farmerId, cropType, plantedAt, weather, "MANUAL");
    }

    public boolean saveFromApi(UUID farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather) {
        return save(farmerId, cropType, plantedAt, weather, "OPEN_METEO");
    }

    public Optional<DailyWeather> findFromApi(UUID farmerId, CropType cropType, OffsetDateTime plantedAt) {
        String source = jdbc.sql("""
                SELECT weather_source FROM farm.crop_entry
                WHERE farmer_id = :farmerId AND crop_type = :cropType AND planted_at = :plantedAt
                """)
                .param("farmerId", farmerId).param("cropType", cropType.name()).param("plantedAt", plantedAt)
                .query(String.class).optional().orElse("");
        return source.equals("OPEN_METEO") ? find(farmerId, cropType, plantedAt) : Optional.empty();
    }

    private boolean save(UUID farmerId, CropType cropType, OffsetDateTime plantedAt, DailyWeather weather, String source) {
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
