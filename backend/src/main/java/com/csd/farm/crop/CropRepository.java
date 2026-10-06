package com.csd.farm.crop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
/**
 * Represents a repository where each entry represents a future harvest of crops
 * that a farmer has planted.
 */
public class CropRepository {

    private final JdbcClient jdbc;

    /**
     * Singular constructor for a CropRepository.
     * @param jdbc The JdbcCilent linked to this repository.
     */
    public CropRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates a new entry in the CropRepository.
     * @param farmerId The ID of the farmer of this crop.
     * @param crop The crop that has been planted.
     */
    public void save(Long farmerId, CropEntry crop) {
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

    /**
     * Reads for all crops that a farmer has planted.
     * @param farmerId The Id of the farmer in the read request.
     * @return A List of all crops that the farmer has planted.
     */
    public List<CropEntry> findAllForFarmer(Long farmerId) {
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

    /**
     * Reads for a specific crop that a farmer has planted at a certain time.
     * @param farmerId The Id of the farmer in the read request.
     * @param cropType The crop that is being requested.
     * @param plantedAt The reqeusted date and time at which the crop has been planted.
     * @return The crop to be read, which may or may not exist.
     */
    public Optional<CropEntry> findForFarmer(Long farmerId, CropType cropType, OffsetDateTime plantedAt) {
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

    /**
     * Creates a CropEntry object from a specific row in a ResultSet.
     * @param row The ResultSet to be read.
     * @param rowNumber The specific row number to read from.
     * @return The CropEntry that has been created.
     * @throws SQLException When something has gone wrong in accessing the database.
     */
    private CropEntry mapRow(ResultSet row, int rowNumber) throws SQLException {
        return new CropEntry(
                CropType.valueOf(row.getString("crop_type")),
                row.getObject("planted_at", OffsetDateTime.class),
                row.getDouble("latitude"),
                row.getDouble("longitude"));
    }
}

