package com.csd.farm;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import com.csd.farm.auth.Farmer;
import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresSchemaTest {
    @Autowired JdbcClient jdbc;
    @Autowired FarmerRepository farmers;
    @Autowired CropRepository crops;
    @Autowired CropWeatherRepository weather;
    @Autowired CropTypeRepository types;
    private Long owner;
    private Long otherOwner;

    @BeforeEach
    void createOwners() {
        jdbc.sql("DELETE FROM public.\"USERS\"").update();
        owner = farmers.save(new Farmer(null, "schema_owner", "owner@example.com", false, false), "unused-test-hash").id();
        otherOwner = farmers.save(new Farmer(null, "schema_other", "other@example.com", false, false), "unused-test-hash").id();
    }

    @Test
    void expectedTablesAndForeignKeysLiveInPublicOnPostgres() {
        assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).isEqualTo("farm_ci");
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables WHERE table_schema='public'
                """).query(String.class).list();
        assertThat(tables).contains("USERS", "FARMS", "CROP_TYPES", "CROP_CYCLES",
                "SENSORS", "RISK_ASSESSMENTS", "RECOMMENDATIONS", "ACCOUNT_STATUS",
                "ACCOUNT_TOKENS", "CROP_WEATHER_READINGS");
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema='public' AND table_name='CROP_WEATHER_READINGS'
                  AND constraint_type='FOREIGN KEY'
                """).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void allConfiguredCropTypesRoundTripThroughTheNewRepositories() {
        assertThat(types.findAll()).hasSize(CropType.values().length);
        for (CropType type : CropType.values()) {
            CropEntry saved = planting(owner, type);
            assertThat(saved.cropCycleId()).isPositive();
            assertThat(crops.findForFarmer(owner, saved.cropCycleId())).contains(saved);
        }
        assertThat(crops.findAllForFarmer(owner)).hasSize(CropType.values().length);
    }

    @Test
    void suppliedFourteenDayAverageIsStoredByCycleWithoutChangingAnotherPlanting() {
        CropEntry first = planting(owner, CropType.POTATO);
        CropEntry second = planting(otherOwner, CropType.POTATO);
        // A supplied aggregate fixture: persistence is tested here, not the upstream aggregation algorithm.
        double average = IntStream.range(20, 34).average().orElseThrow();
        DailyWeather aggregate = new DailyWeather(LocalDate.of(2026, 10, 31), "Asia/Singapore",
                average, 25200.0, 82.0, 0.25);
        assertThat(weather.saveFromApi(first.cropCycleId(), aggregate)).isTrue();
        assertThat(weather.find(first.cropCycleId())).contains(aggregate);
        assertThat(weather.find(second.cropCycleId())).isEmpty();
        assertThat(crops.findForFarmer(otherOwner, first.cropCycleId())).isEmpty();
        assertThat(weather.saveFromApi(first.cropCycleId(), aggregate)).isTrue();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM public.\"CROP_WEATHER_READINGS\"")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void olderAggregateCannotOverwriteNewerApiData() {
        Long cycle = planting(owner, CropType.POTATO).cropCycleId();
        DailyWeather newer = new DailyWeather(LocalDate.of(2026, 10, 31), "Asia/Singapore", 26.5, 25200.0, 82.0, 0.25);
        DailyWeather older = new DailyWeather(LocalDate.of(2026, 10, 30), "Asia/Singapore", 10.0, null, null, null);
        assertThat(weather.saveFromApi(cycle, newer)).isTrue();
        assertThat(weather.saveFromApi(cycle, older)).isFalse();
        assertThat(weather.findFromApi(cycle)).contains(newer);
    }

    @Test
    void weatherMustReferenceAnExistingCycle() {
        assertThatThrownBy(() -> weather.saveFromApi(-1L,
                new DailyWeather(LocalDate.of(2026, 10, 31), "Asia/Singapore", 26.5, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidWeatherValuesAreRejectedByTheDatabase() {
        Long cycle = planting(owner, CropType.POTATO).cropCycleId();
        assertThatThrownBy(() -> weather.saveFromApi(cycle,
                new DailyWeather(LocalDate.of(2026, 10, 31), "Asia/Singapore", 26.5, null, null, 25.0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyTheOwnerCanDeleteACycleAndItsWeatherIsRemovedWithIt() {
        Long cycle = planting(owner, CropType.POTATO).cropCycleId();
        weather.saveFromApi(cycle, new DailyWeather(LocalDate.of(2026, 10, 31), "Asia/Singapore", 26.5, null, null, null));
        assertThat(crops.deleteForFarmer(otherOwner, cycle)).isFalse();
        assertThat(weather.find(cycle)).isPresent();
        assertThat(crops.deleteForFarmer(owner, cycle)).isTrue();
        assertThat(weather.find(cycle)).isEmpty();
    }

    @Test
    void domainRelationsRejectOrphanedParents() {
        for (String statement : List.of(
                "INSERT INTO public.\"FARMS\" (\"User_ID\",\"Farm_Name\",\"Latitude\",\"Longitude\") VALUES (-1,'Orphan',1,1)",
                "INSERT INTO public.\"SENSORS\" (\"Farm_ID\") VALUES (-1)",
                "INSERT INTO public.\"CROP_CYCLES\" (\"Farm_ID\",\"Crop_Type_ID\",\"Date_Planted\") VALUES (-1,-1,CURRENT_TIMESTAMP)",
                "INSERT INTO public.\"RISK_ASSESSMENTS\" (\"Crop_Cycle_ID\") VALUES (-1)",
                "INSERT INTO public.\"RECOMMENDATIONS\" (\"Assessment_ID\") VALUES (-1)")) {
            assertThatThrownBy(() -> jdbc.sql(statement).update()).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private CropEntry planting(Long user, CropType type) {
        return crops.save(user, new CropEntry(null, "CI Farm", type,
                OffsetDateTime.parse("2026-01-10T08:30:00Z"), 1.3521, 103.8198));
    }
}
