package com.csd.farm.crop;

import java.time.LocalDate;
import com.csd.farm.crop.BiomassFormula.Weather;
import com.csd.farm.crop.CropResponses.Cause;
import com.csd.farm.crop.CropResponses.Prediction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BiomassFormulaTest {
    // Maize profile: optimum 25 °C, 0.28 m³/m³, 1.2 kPa, 9 h/day; target 10000 at 150 days.
    private static final Weather OPTIMAL = new Weather(25, 0.28, 1.2, 9);
    private static final LocalDate PLANTED = LocalDate.of(2026, 5, 1);
    private static final LocalDate TODAY = PLANTED.plusDays(50);

    private final BiomassFormula formula = new BiomassFormula();

    @Test
    void optimalWeatherReachesTheTargetWithNoCauses() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, TODAY, OPTIMAL);

        assertThat(result.status()).isEqualTo("ESTIMATE");
        assertThat(result.value()).isCloseTo(10000, within(1e-6));
        assertThat(result.referenceEstimate()).isCloseTo(10000, within(1e-6));
        assertThat(result.projectionDate()).isEqualTo(PLANTED.plusDays(150).toString());
        assertThat(result.causes()).isEmpty();
    }

    @Test
    void drySoilIsReportedAsTheOnlyCause() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(25, 0.18, 1.2, 9));

        assertThat(result.value()).isCloseTo(8000, within(1e-6));
        assertThat(result.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.factor()).isEqualTo("Soil moisture");
            assertThat(cause.direction()).isEqualTo("too low");
            assertThat(cause.shortfallPercent()).isCloseTo(20, within(1e-6));
            assertThat(cause.actual()).isEqualTo(0.18);
            assertThat(cause.optimal()).isEqualTo(0.28);
        });
    }

    @Test
    void causesAddUpToTheShortfallAndAreSortedLargestFirst() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(30, 0.20, 1.2, 9));

        // Heat: 0.025 * 5 = 12.5%. Dry soil: 2 * 0.08 = 16%.
        assertThat(result.value()).isCloseTo(7150, within(1e-6));
        assertThat(result.causes()).extracting(Cause::factor).containsExactly("Soil moisture", "Temperature");
        assertThat(result.causes().get(1).direction()).isEqualTo("too high");
        double total = result.causes().stream().mapToDouble(Cause::shortfallPercent).sum();
        assertThat(total).isCloseTo(100 * (1 - result.value() / 10000), within(1e-6));
    }

    @Test
    void severeStressIsCappedAtZeroAndCausesSumToOneHundredPercent() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(-40, 0, 1.2, 9));

        assertThat(result.value()).isZero();
        double total = result.causes().stream().mapToDouble(Cause::shortfallPercent).sum();
        assertThat(total).isCloseTo(100, within(1e-6));
    }

    @Test
    void currentEstimateFollowsTheGrowthCurve() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(25, 0.18, 1.2, 9));

        // Maize curve has 2527 at day 50; dry soil leaves 80%.
        assertThat(result.currentEstimate()).isCloseTo(2527 * 0.8, within(1e-6));
    }

    @Test
    void cropsWithoutAProfileAreUnsupported() {
        assertThat(formula.estimate(CropType.SUGAR_BEET, PLANTED, TODAY, OPTIMAL).status()).isEqualTo("UNSUPPORTED");
        assertThat(formula.reference(CropType.SUGAR_BEET, PLANTED, TODAY, "x").status()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void plantingsPastFullGrowthAreUnsupported() {
        Prediction result = formula.estimate(CropType.MAIZE, PLANTED, PLANTED.plusDays(151), OPTIMAL);

        assertThat(result.status()).isEqualTo("UNSUPPORTED");
        assertThat(result.message()).contains("151 days");
    }

    @Test
    void outOfRangeWeatherIsRejected() {
        assertThat(formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(25, 0.9, 1.2, 9)).status())
                .isEqualTo("INVALID_INPUT");
        assertThat(formula.estimate(CropType.MAIZE, PLANTED, TODAY, new Weather(Double.NaN, 0.28, 1.2, 9)).status())
                .isEqualTo("INVALID_INPUT");
    }

    @Test
    void referenceGivesTheTargetWithoutAnEstimate() {
        Prediction result = formula.reference(CropType.MAIZE, PLANTED, TODAY, "No weather yet.");

        assertThat(result.status()).isEqualTo("REFERENCE_ONLY");
        assertThat(result.value()).isNull();
        assertThat(result.referenceEstimate()).isCloseTo(10000, within(1e-6));
        assertThat(result.message()).isEqualTo("No weather yet.");
    }

    @Test
    void interpolationIsClampedAtBothEnds() {
        double[] xs = {0, 10, 20};
        double[] ys = {0, 100, 300};
        assertThat(BiomassFormula.interpolate(-5, xs, ys)).isZero();
        assertThat(BiomassFormula.interpolate(15, xs, ys)).isEqualTo(200);
        assertThat(BiomassFormula.interpolate(99, xs, ys)).isEqualTo(300);
    }
}
