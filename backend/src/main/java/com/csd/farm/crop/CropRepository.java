package com.csd.farm.crop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CropRepository {

    private final JdbcClient jdbc;

    public CropRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID farmerId, CropEntry crop) {
        jdbc.sql("""
                INSERT INTO farm.crop_entry (farmer_id, crop_type, planted_at, latitude, longitude)
                VALUES (:farmerId, :cropType, :plantedAt, :latitude, :longitude)
                """)
                .param("farmerId", farmerId)
                .param("cropType", crop.cropType().name())
                .param("plantedAt", crop.plantedAt())
                .param("latitude", crop.latitude())
                .param("longitude", crop.longitude())
                .update();
    }

    public List<CropEntry> findAllForFarmer(UUID farmerId) {
        return jdbc.sql("""
                SELECT crop_type, planted_at, latitude, longitude
                FROM farm.crop_entry
                WHERE farmer_id = :farmerId
                ORDER BY planted_at DESC, crop_type
                """)
                .param("farmerId", farmerId)
                .query(this::mapRow)
                .list();
    }

    public Optional<CropEntry> findForFarmer(UUID farmerId, CropType cropType, OffsetDateTime plantedAt) {
        return jdbc.sql("""
                SELECT crop_type, planted_at, latitude, longitude
                FROM farm.crop_entry
                WHERE farmer_id = :farmerId AND crop_type = :cropType AND planted_at = :plantedAt
                """)
                .param("farmerId", farmerId)
                .param("cropType", cropType.name())
                .param("plantedAt", plantedAt)
                .query(this::mapRow)
                .optional();
    }

    private CropEntry mapRow(ResultSet row, int rowNumber) throws SQLException {
        return new CropEntry(
                CropType.valueOf(row.getString("crop_type")),
                row.getObject("planted_at", OffsetDateTime.class),
                row.getBigDecimal("latitude"),
                row.getBigDecimal("longitude"));
    }
}
