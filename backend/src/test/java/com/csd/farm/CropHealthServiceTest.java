package com.csd.farm;

import java.time.LocalDate;

import com.csd.farm.crop.CropConditions;
import com.csd.farm.crop.CropHealthService;
import com.csd.farm.crop.CropType;
import com.csd.farm.crop.DailyWeather;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CropHealthServiceTest {

    private final CropHealthService health = new CropHealthService();

    @ParameterizedTest
    @EnumSource(CropType.class)
    void allCropsIncludeTheirBoundariesAndDetectValuesOutsideThem(CropType type) {
        var conditions = CropConditions.forCrop(type);
        double minimumTemperature = conditions.temperatureC().minimum();
        double maximumTemperature = conditions.temperatureC().maximum();
        double minimumSeconds = conditions.sunshineHours().minimum() * 3600;
        double maximumSeconds = conditions.sunshineHours().maximum() * 3600;

        var atMinimum = health.assess(type, weather(minimumTemperature, minimumSeconds));
        var atMaximum = health.assess(type, weather(maximumTemperature, maximumSeconds));
        assertThat(atMinimum.readings().get(0).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMinimum.readings().get(3).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMaximum.readings().get(0).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMaximum.readings().get(3).status()).isEqualTo("WITHIN_RANGE");
        assertThat(atMinimum.needsAttention()).isFalse();
        assertThat(atMaximum.needsAttention()).isFalse();

        var below = health.assess(type, weather(minimumTemperature - 0.01, minimumSeconds - 1));
        assertThat(below.readings().get(0).status()).isEqualTo("BELOW_RANGE");
        assertThat(below.readings().get(3).status()).isEqualTo("BELOW_RANGE");
        assertThat(below.needsAttention()).isTrue();
        assertThat(below.issues()).hasSize(2);

        var above = health.assess(type, weather(maximumTemperature + 0.01, maximumSeconds + 1));
        assertThat(above.readings().get(0).status()).isEqualTo("ABOVE_RANGE");
        assertThat(above.readings().get(3).status()).isEqualTo("ABOVE_RANGE");
        assertThat(above.needsAttention()).isTrue();
        assertThat(above.issues()).hasSize(1);
    }

    @Test
    void secondsBecomeHoursAndLongSunshineAloneDoesNotFlagDamage() {
        var result = health.assess(CropType.POTATO, weather(20.0, 32400.0));
        assertThat(result.readings().get(3).value()).isEqualTo(9.0);
        assertThat(result.readings().get(3).status()).isEqualTo("ABOVE_RANGE");
        assertThat(result.needsAttention()).isFalse();
        assertThat(result.issues()).isEmpty();
        assertThat(result.recommendedActions()).anyMatch(action -> action.contains("Do not add shade solely"));
    }

    @Test
    void missingReadingsAreNotAssessedButZeroSunshineIsBelowRange() {
        assertThat(health.assess(CropType.POTATO, null).readings())
                .allMatch(reading -> reading.status().equals("NOT_ASSESSED") && reading.value() == null);
        var partial = health.assess(CropType.POTATO, weather(null, 0.0));
        assertThat(partial.readings().get(0).status()).isEqualTo("NOT_ASSESSED");
        assertThat(partial.readings().get(3).status()).isEqualTo("BELOW_RANGE");
        assertThat(partial.needsAttention()).isTrue();
    }

    @Test
    void humidityAndSoilMoistureStayUnassessedEvenWhenValuesArePresent() {
        var result = health.assess(CropType.SUGAR_CANE, weather(30.0, 28800.0));
        assertThat(result.readings().get(1).value()).isEqualTo(82.0);
        assertThat(result.readings().get(2).value()).isEqualTo(25.0);
        assertThat(result.readings().get(2).unit()).isEqualTo("% by volume");
        assertThat(result.readings().get(1).status()).isEqualTo("NOT_ASSESSED");
        assertThat(result.readings().get(2).status()).isEqualTo("NOT_ASSESSED");
        assertThat(result.readings().get(1).referenceRange()).isNull();
        assertThat(result.readings().get(2).referenceRange()).isNull();
    }

    private DailyWeather weather(Double temperature, Double sunshineSeconds) {
        return new DailyWeather(LocalDate.of(2026, 1, 11), "Asia/Singapore",
                temperature, sunshineSeconds, 82.0, 0.25);
    }
}
