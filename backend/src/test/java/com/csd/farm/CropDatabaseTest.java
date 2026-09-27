package com.csd.farm;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.csd.farm.crop.CropEntry;
import com.csd.farm.crop.CropRepository;
import com.csd.farm.crop.CropType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CropDatabaseTest {

    @Test
    void cropsCanBeSavedAfterTheSchemaConnectionCloses() throws Exception {
        String url = "jdbc:h2:mem:crop-reconnect-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        // H2 2.4.240 broke CHECK (crop_type IN (...)) after this connection closed.
        // Simulate connection retirement immediately, without waiting 30 minutes.
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA farm");
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_farm_tables.sql"));
        }

        var jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));
        var crops = new CropRepository(jdbc);
        UUID farmerId = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO farm.farmer_account (id, username, display_name, password_hash)
                    VALUES (?, 'test_farmer', 'Test farmer', 'unused-test-hash')
                    """).param(farmerId).update();

            for (CropType type : CropType.values()) {
                crops.save(farmerId, new CropEntry(type,
                        OffsetDateTime.parse("2026-01-10T08:30:00Z"),
                        BigDecimal.ONE, BigDecimal.ONE));
            }
            assertThat(crops.findAllForFarmer(farmerId)).hasSize(10);

            // The fix must preserve the database's crop-type validation.
            assertThatThrownBy(() -> jdbc.sql("""
                    INSERT INTO farm.crop_entry (farmer_id, crop_type, planted_at, latitude, longitude)
                    VALUES (?, 'UNKNOWN', CURRENT_TIMESTAMP, 1, 1)
                    """).param(farmerId).update())
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.sql("SHUTDOWN").update();
        }
    }
}
