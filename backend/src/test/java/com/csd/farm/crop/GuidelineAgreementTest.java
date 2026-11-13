package com.csd.farm.crop;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.csd.farm.ai.AIRecommenderService;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.GuidelineAgreement;
import com.csd.farm.crop.crop_category.GuidelineAgreement.Level;

class GuidelineAgreementTest {

    private final Crop tomato = CropConditions.forCrop(CropType.TOMATO);
    private final Crop wheat = CropConditions.forCrop(CropType.WHEAT);

    @Test
    void allExpectedActionsPresentIsHighWithNothingMissing() {
        // Tomato, severe heat: RELOCATE, REMOVE_DAMAGED, WATER.
        var guidelines = tomato.guidelinesFor("ABOVE_RANGE_SEVERE", "WITHIN_RANGE");
        var agreement = GuidelineAgreement.of(guidelines, List.of(RELOCATE, REMOVE_DAMAGED, WATER));

        assertThat(agreement.level()).isEqualTo(Level.HIGH);
        assertThat(agreement.missing()).isEmpty();
        assertThat(agreement.matched()).containsExactlyInAnyOrder(RELOCATE, REMOVE_DAMAGED, WATER);
        assertThat(agreement.score()).isEqualTo(1.0);
        assertThat(GuidelineAgreement.score(guidelines, List.of(RELOCATE, REMOVE_DAMAGED, WATER))).isEqualTo(1.0);
    }

    @Test
    void inRangeReadingsAgreeWithMonitoringAndPenaliseInterventions() {
        var guidelines = wheat.guidelinesFor("WITHIN_RANGE", "WITHIN_RANGE");

        assertThat(GuidelineAgreement.of(guidelines, List.of(MONITOR)).level()).isEqualTo(Level.HIGH);
        assertThat(GuidelineAgreement.of(guidelines, List.of()).level()).isEqualTo(Level.HIGH);
        assertThat(GuidelineAgreement.score(guidelines, List.of(MONITOR))).isEqualTo(1.0);

        assertThat(GuidelineAgreement.score(guidelines, List.of(MONITOR, WATER))).isLessThan(1.0);
        assertThat(GuidelineAgreement.score(guidelines, List.of(WATER))).isLessThan(1.0);
        assertThat(GuidelineAgreement.of(guidelines, List.of(WATER)).unexpected()).containsExactly(WATER);
    }

    @Test
    void contraindicatedActionLowersScoreAndIsReportedAsUnexpected() {
        // Wheat, mild heat: SHADE and WATER expected; protecting from cold makes heat stress worse.
        var guidelines = wheat.guidelinesFor("ABOVE_RANGE", "WITHIN_RANGE");
        double clean = GuidelineAgreement.score(guidelines, List.of(SHADE, WATER));
        double harmful = GuidelineAgreement.score(guidelines, List.of(SHADE, WATER, PROTECT_FROM_COLD));

        assertThat(harmful).isLessThan(clean);
        assertThat(GuidelineAgreement.of(guidelines, List.of(SHADE, WATER, PROTECT_FROM_COLD)).unexpected())
                .containsExactly(PROTECT_FROM_COLD);
    }

    @Test
    void fieldCropsAreNotExpectedToRelocate() {
        assertThat(wheat.guidelinesFor("BELOW_RANGE_SEVERE", "BELOW_RANGE_SEVERE"))
                .allSatisfy(g -> assertThat(g.expected()).doesNotContainKey(RELOCATE));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not json", "{\"actions\":[\"WATER\"]}", "{\"recommendation\":\"  \",\"actions\":[]}", "[1,2]"})
    void unparseableOrEmptyAiOutputIsNoResult(String output) {
        assertThat(AIRecommenderService.parse(output)).isEmpty();
    }

    @Test
    void unknownAiActionsAreDropped() {
        var advice = AIRecommenderService.parse(
                "{\"recommendation\":\"Water deeply.\",\"actions\":[\"WATER\",\"PRAY\",\"WATER\",3]}").orElseThrow();
        assertThat(advice.recommendation()).isEqualTo("Water deeply.");
        assertThat(advice.actions()).containsExactly(WATER);
    }

    @ParameterizedTest
    @EnumSource(CropType.class)
    void everyConfiguredCropHasAGuidelineForEveryOutOfRangeStatus(CropType type) {
        Crop crop = CropConditions.forCrop(type);
        if (crop == null) return;
        for (String status : Crop.OUT_OF_RANGE_STATUSES) {
            assertThat(crop.getTemperatureGuidelines()).as(type + " temperature " + status).containsKey(status);
            assertThat(crop.getSunshineGuidelines()).as(type + " sunshine " + status).containsKey(status);
            assertThat(crop.getTemperatureGuidelines().get(status).expected()).isNotEmpty();
            assertThat(crop.getSunshineGuidelines().get(status).expected()).isNotEmpty();
        }
    }
}
