package com.csd.farm.crop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CropDatabaseTest {

    private static final Long FARMER_A = 1L;
    private static final Long FARMER_B = 2L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("crop_test")
                    .withUsername("test")
                    .withPassword("test");

    private JdbcClient jdbc;
    private FarmRepository farms;
    private CropRepository crops;

    @BeforeEach
    void setUp() throws SQLException {

        // Create the schema using an initial connection.
        // This connection will close before JdbcClient is created.
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             Statement statement = connection.createStatement()) {

            createTables(statement);
        }

        // Create a new data source after the schema connection closes.
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        jdbc = JdbcClient.create(dataSource);

        // Start every test with clean tables and reset identity sequences.
        jdbc.sql("""
                TRUNCATE TABLE
                    public."CROP_CYCLES",
                    public."FARMS",
                    public."CROP_TYPES",
                    public."USERS"
                RESTART IDENTITY CASCADE
                """).update();

        FarmRepository farmRepository = new FarmRepository(jdbc);
        CropTypeRepository cropTypeRepository = new CropTypeRepository(jdbc);

        farms = farmRepository;
        crops = new CropRepository(jdbc, farmRepository, cropTypeRepository);

        insertUser(FARMER_A, "test_farmer_a", "test-a@example.com");
        insertUser(FARMER_B, "test_farmer_b", "test-b@example.com");

        // Insert every crop type so CropTypeRepository.idFor() can find them.
        for (CropType type : CropType.values()) {
            jdbc.sql("""
                    INSERT INTO public."CROP_TYPES" ("Crop_Name")
                    VALUES (:name)
                    """)
                    .param("name", type.displayName())
                    .update();
        }
    }

    @Test
    void cropsCanBeSavedAfterTheSchemaConnectionCloses() {

        // The schema-creation connection has already closed.
        // All database operations below use the new JdbcClient connection.

        OffsetDateTime plantedAt =
                OffsetDateTime.parse("2026-01-10T08:30:00Z");

        for (CropType type : CropType.values()) {
            crops.save(FARMER_A, new CropEntry(
                    null,
                    "Test Farm",
                    type,
                    plantedAt,
                    1.0,
                    1.0
            ));
        }

        assertThat(crops.findAllForFarmer(FARMER_A))
                .hasSize(CropType.values().length);

        assertThat(countFarms(FARMER_A, "Test Farm"))
                .isEqualTo(1L);
    }

    @Test
    void findOrCreate_WhenFarmDoesNotExist_CreatesFarm() {

        FarmLocation createdFarm = farms.findOrCreate(
                FARMER_A, "Main Farm", 1.35, 103.82
        );

        assertThat(createdFarm.farmId()).isPositive();
        assertThat(createdFarm.latitude()).isEqualTo(1.35);
        assertThat(createdFarm.longitude()).isEqualTo(103.82);

        assertThat(countFarms(FARMER_A, "Main Farm"))
                .isEqualTo(1L);
    }

    @Test
    void findOrCreate_WhenFarmAlreadyExists_ReusesStoredCoordinates() {

        FarmLocation originalFarm = farms.findOrCreate(
                FARMER_A, "Main Farm", 1.35, 103.82
        );

        FarmLocation reusedFarm = farms.findOrCreate(
                FARMER_A, "Main Farm", 1.40, 103.90
        );

        assertThat(reusedFarm.farmId())
                .isEqualTo(originalFarm.farmId());

        assertThat(reusedFarm.latitude()).isEqualTo(1.35);
        assertThat(reusedFarm.longitude()).isEqualTo(103.82);

        assertThat(countFarms(FARMER_A, "Main Farm"))
                .isEqualTo(1L);
    }

    @Test
    void findOrCreate_WhenDifferentUsersUseSameFarmName_CreatesSeparateFarms() {

        FarmLocation farmerAFarm = farms.findOrCreate(
                FARMER_A, "Main Farm", 1.35, 103.82
        );

        FarmLocation farmerBFarm = farms.findOrCreate(
                FARMER_B, "Main Farm", 1.40, 103.90
        );

        assertThat(farmerAFarm.farmId())
                .isNotEqualTo(farmerBFarm.farmId());

        assertThat(farmerAFarm.latitude()).isEqualTo(1.35);
        assertThat(farmerAFarm.longitude()).isEqualTo(103.82);

        assertThat(farmerBFarm.latitude()).isEqualTo(1.40);
        assertThat(farmerBFarm.longitude()).isEqualTo(103.90);

        assertThat(countFarms(FARMER_A, "Main Farm"))
                .isEqualTo(1L);

        assertThat(countFarms(FARMER_B, "Main Farm"))
                .isEqualTo(1L);
    }

    @Test
    void save_WhenFarmAlreadyExists_UsesExistingFarmIdAndCoordinates() {

        FarmLocation existingFarm = farms.findOrCreate(
                FARMER_A, "Main Farm", 1.35, 103.82
        );

        // Deliberately submit coordinates different from the stored farm.
        CropEntry submittedCrop = new CropEntry(
                null,
                "Main Farm",
                CropType.POTATO,
                OffsetDateTime.parse("2026-01-10T08:30:00Z"),
                1.40,
                103.90
        );

        CropEntry savedCrop = crops.save(FARMER_A, submittedCrop);

        assertThat(crops.findFarmIdForCrop(
                FARMER_A, savedCrop.cropCycleId()
        )).contains(existingFarm.farmId());

        assertThat(savedCrop.latitude()).isEqualTo(1.35);
        assertThat(savedCrop.longitude()).isEqualTo(103.82);

        assertThat(countFarms(FARMER_A, "Main Farm"))
                .isEqualTo(1L);

        assertThat(crops.countForFarm(existingFarm.farmId()))
                .isEqualTo(1L);
    }

    // Helper methods

    private void insertUser(Long userId, String username, String email) {
        jdbc.sql("""
                INSERT INTO public."USERS"
                    ("User_ID", "User_Name", "User_Email", "Password_Hash")
                VALUES (:id, :username, :email, 'unused-test-hash')
                """)
                .param("id", userId)
                .param("username", username)
                .param("email", email)
                .update();
    }

    private long countFarms(Long userId, String farmName) {
        return jdbc.sql("""
                SELECT COUNT(*)
                FROM public."FARMS"
                WHERE "User_ID" = :userId
                  AND "Farm_Name" = :farmName
                """)
                .param("userId", userId)
                .param("farmName", farmName)
                .query(Long.class)
                .single();
    }

    private void createTables(Statement statement) throws SQLException {

        statement.execute("""
                CREATE TABLE IF NOT EXISTS public."USERS" (
                    "User_ID" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    "User_Name" VARCHAR(40) NOT NULL UNIQUE,
                    "User_Email" VARCHAR(320) NOT NULL UNIQUE,
                    "Password_Hash" VARCHAR(255) NOT NULL,
                    "Role" VARCHAR(6) NOT NULL DEFAULT 'FARMER',
                    CONSTRAINT "USERS_Role_check"
                        CHECK ("Role" IN ('FARMER', 'ADMIN'))
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS public."CROP_TYPES" (
                    "Crop_Type_ID" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    "Crop_Name" VARCHAR(80) NOT NULL,
                    "Variety" VARCHAR(100),
                    "Optimal_Temp_Min" NUMERIC(6, 2),
                    "Optimal_Temp_Max" NUMERIC(6, 2),
                    "Optimal_Soil_Moisture_Min" NUMERIC(7, 4),
                    "Optimal_Soil_Moisture_Max" NUMERIC(7, 4),
                    "Optimal_pH_Min" NUMERIC(4, 2),
                    "Optimal_pH_Max" NUMERIC(4, 2),
                    "Optimal_Light_Min" NUMERIC(10, 2),
                    "Optimal_Light_Max" NUMERIC(10, 2)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS public."FARMS" (
                    "Farm_ID" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    "User_ID" BIGINT NOT NULL,
                    "Farm_Name" VARCHAR(100) NOT NULL,
                    "Latitude" NUMERIC(9, 6) NOT NULL,
                    "Longitude" NUMERIC(9, 6) NOT NULL,
                    CONSTRAINT farms_user_name_unique
                        UNIQUE ("User_ID", "Farm_Name"),
                    CONSTRAINT "FARMS_User_ID_fkey"
                        FOREIGN KEY ("User_ID")
                        REFERENCES public."USERS" ("User_ID")
                        ON DELETE CASCADE,
                    CONSTRAINT "FARMS_Latitude_check"
                        CHECK ("Latitude" >= -90 AND "Latitude" <= 90),
                    CONSTRAINT "FARMS_Longitude_check"
                        CHECK ("Longitude" >= -180 AND "Longitude" <= 180)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS public."CROP_CYCLES" (
                    "Crop_Cycle_ID" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    "Farm_ID" BIGINT NOT NULL,
                    "Crop_Type_ID" BIGINT NOT NULL,
                    "Date_Planted" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "Expected_Harvest_Date" DATE,
                    "Actual_Harvest_Date" DATE,
                    CONSTRAINT crop_cycles_farm_type_planting_unique
                        UNIQUE ("Farm_ID", "Crop_Type_ID", "Date_Planted"),
                    CONSTRAINT "CROP_CYCLES_Crop_Type_ID_fkey"
                        FOREIGN KEY ("Crop_Type_ID")
                        REFERENCES public."CROP_TYPES" ("Crop_Type_ID")
                        ON DELETE RESTRICT,
                    CONSTRAINT "CROP_CYCLES_Farm_ID_fkey"
                        FOREIGN KEY ("Farm_ID")
                        REFERENCES public."FARMS" ("Farm_ID")
                        ON DELETE CASCADE,
                    CONSTRAINT crop_cycles_expected_date
                        CHECK (
                            "Expected_Harvest_Date" IS NULL
                            OR "Expected_Harvest_Date" >= "Date_Planted"::date
                        )
                )
                """);
    }
}