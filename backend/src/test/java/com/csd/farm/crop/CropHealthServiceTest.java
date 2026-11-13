package com.csd.farm.crop;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CropHealthServiceTest {

    private final CropHealthService health = new CropHealthService();

    // Crop types without reference conditions are covered by cropsWithoutConditionsAreNotAssessed.
    @ParameterizedTest
    @EnumSource(value = CropType.class,
            names = {"SUGAR_BEET", "SPRING_WHEAT", "FABA_BEAN", "WINTER_BARLEY", "RAPESEED", "SPELT"},
            mode = EnumSource.Mode.EXCLUDE)
    void allCropsIncludeTheirBoundariesAndDetectValuesOutsideThem(CropType type) {
        var conditions = CropConditions.forCrop(type);
        double minimumTemperature = conditions.getTemperatureRange().getOptimalMinimum();
        double maximumTemperature = conditions.getTemperatureRange().getOptimalMaximum();
        double minimumSeconds = conditions.getSunshineRange().getOptimalMinimum() * 3600;
        double maximumSeconds = conditions.getSunshineRange().getOptimalMaximum() * 3600;

        var atMinimum = health.assess(type, weather(minimumTemperature, minimumSeconds));
        var atMaximum = health.assess(type, weather(maximumTemperature, maximumSeconds));
        assertThat(atMinimum.readings().get(0).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMinimum.readings().get(3).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMaximum.readings().get(0).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMaximum.readings().get(3).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMinimum.needsAttention()).isFalse();
        assertThat(atMaximum.needsAttention()).isFalse();

        // Just outside the optimum is ABOVE/BELOW_RANGE, or _SEVERE where a crop's severe limit
        // sits at the optimum edge; temperature and sunshine each raise their own issue.
        var below = health.assess(type, weather(minimumTemperature - 0.01, minimumSeconds - 1));
        assertThat(below.readings().get(0).status()).startsWith("BELOW_RANGE");
        assertThat(below.readings().get(3).status()).startsWith("BELOW_RANGE");
        assertThat(below.needsAttention()).isTrue();
        assertThat(below.issues()).hasSize(2);

        var above = health.assess(type, weather(maximumTemperature + 0.01, maximumSeconds + 1));
        assertThat(above.readings().get(0).status()).startsWith("ABOVE_RANGE");
        assertThat(above.readings().get(3).status()).startsWith("ABOVE_RANGE");
        assertThat(above.needsAttention()).isTrue();
        assertThat(above.issues()).hasSize(2);
    }

    @Test
    void secondsBecomeHoursAndLongSunshineIsFlagged() {
        var result = health.assess(CropType.POTATO, weather(20.0, 30600.0));
        assertThat(result.readings().get(3).value()).isEqualTo(8.5);
        assertThat(result.readings().get(3).status()).isEqualTo("ABOVE_RANGE");
        assertThat(result.needsAttention()).isTrue();
        assertThat(result.issues()).containsExactly("Daily sunshine is above this crop's reference range.");
    }

    @Test
    void severeLimitsAreReported() {
        // Potato: 15-25 °C optimum, severe beyond 7-30 °C.
        assertThat(health.assess(CropType.POTATO, weather(31.0, 25200.0)).readings().get(0).status())
                .isEqualTo("ABOVE_RANGE_SEVERE");
        assertThat(health.assess(CropType.POTATO, weather(6.0, 25200.0)).readings().get(0).status())
                .isEqualTo("BELOW_RANGE_SEVERE");
    }

    @ParameterizedTest
    @EnumSource(value = CropType.class,
            names = {"SUGAR_BEET", "SPRING_WHEAT", "FABA_BEAN", "WINTER_BARLEY", "RAPESEED", "SPELT"})
    void cropsWithoutConditionsAreNotAssessed(CropType type) {
        var result = health.assess(type, weather(30.0, 30600.0));
        assertThat(result.readings()).allMatch(reading -> reading.status().equals("NOT_ASSESSED"));
        assertThat(result.needsAttention()).isFalse();
    }

    @Test
    void missingReadingsAreNotAssessedButZeroSunshineIsBelowRange() {
        assertThat(health.assess(CropType.POTATO, null).readings())
                .allMatch(reading -> reading.status().equals("NOT_ASSESSED") && reading.value() == null);
        var partial = health.assess(CropType.POTATO, weather(null, 0.0));
        assertThat(partial.readings().get(0).status()).isEqualTo("NOT_ASSESSED");
        assertThat(partial.readings().get(3).status()).isEqualTo("BELOW_RANGE_SEVERE");
        assertThat(partial.needsAttention()).isTrue();
    }

    @Test
    void vpdAndSoilMoistureStayUnassessedEvenWhenValuesArePresent() {
        var result = health.assess(CropType.SUGAR_CANE, weather(30.0, 28800.0));
        assertThat(result.readings().get(1).name()).isEqualTo("Vapour pressure deficit");
        assertThat(result.readings().get(1).value()).isEqualTo(1.2);
        assertThat(result.readings().get(1).unit()).isEqualTo("kPa");
        assertThat(result.readings().get(2).value()).isEqualTo(25.0);
        assertThat(result.readings().get(2).unit()).isEqualTo("% by volume");
        assertThat(result.readings().get(1).status()).isEqualTo("NOT_ASSESSED");
        assertThat(result.readings().get(2).status()).isEqualTo("NOT_ASSESSED");
        assertThat(result.readings().get(1).referenceRange()).isNull();
        assertThat(result.readings().get(2).referenceRange()).isNull();
    }

    private DailyWeather weather(Double temperature, Double sunshineSeconds) {
        return new DailyWeather(LocalDate.of(2026, 1, 11), "Asia/Singapore",
                temperature, sunshineSeconds, 82.0, 0.25, 1.2);
    }
}
