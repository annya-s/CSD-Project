package com.csd.farm.crop;

import static com.csd.farm.crop.crop_category.RecommendedAction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.csd.farm.ai.AIRecommenderService;
import com.csd.farm.ai.AIRecommenderService.Advice;
import com.csd.farm.auth.Farmer;
import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.crop_category.GuidelineAgreement.Level;
import com.csd.farm.environment.EnvironmentReading;
import com.csd.farm.environment.EnvironmentService;

class CropServiceAssessmentTest {
    private final CropRepository crops = mock(CropRepository.class);
    private final FarmerRepository farmers = mock(FarmerRepository.class);
    private final EnvironmentService environment = mock(EnvironmentService.class);
    private final AIRecommenderService ai = mock(AIRecommenderService.class);
    private final CropService service = new CropService(crops, mock(CropTypeRepository.class), farmers,
            mock(FarmRepository.class), mock(CropWeatherRepository.class), new CropHealthService(),
            mock(CropWeatherService.class), mock(BiomassEstimateService.class), environment,
            mock(JdbcClient.class), ai);

    @BeforeEach
    void setUp() {
        when(farmers.findByUsername("farmer")).thenReturn(Optional.of(new Farmer(1L, "farmer", "f@example.com", true, false, "USER")));
        when(crops.findForFarmer(1L, 10L)).thenReturn(Optional.of(
                new CropEntry(10L, "Main Farm", CropType.WHEAT, OffsetDateTime.parse("2026-01-10T08:30:00Z"), 1.0, 1.0)));
    }

    @Test
    void noAiResultMeansNoRecommendationAndNoAgreement() {
        when(environment.getHistoricalReading(10)).thenReturn(days(14, 20.0, 7.0));
        when(ai.getRecommendation(any(), any())).thenReturn(Optional.empty());

        var result = service.assessWithAI("farmer", 10L);
        assertThat(result.recommendation()).isNull();
        assertThat(result.agreement()).isNull();
    }

    @Test
    void healthyWindowAndMonitorOnlyIsHigh() {
        when(environment.getHistoricalReading(10)).thenReturn(days(14, 20.0, 7.0));
        when(ai.getRecommendation(any(), any())).thenReturn(Optional.of(new Advice("All fine.", List.of(MONITOR))));

        var agreement = service.assessWithAI("farmer", 10L).agreement();
        assertThat(agreement.level()).isEqualTo(Level.HIGH);
        assertThat(agreement.matched()).containsExactly(MONITOR);
    }

    @Test
    void statusComesFromTheWholeWindowNotTheLatestDay() {
        // 13 hot days (wheat optimum 15-23 °C) followed by one mild day: the window is still too hot.
        var readings = new java.util.ArrayList<>(days(13, 26.0, 7.0));
        readings.addAll(day(LocalDate.of(2026, 2, 1), 20.0, 7.0));
        when(environment.getHistoricalReading(10)).thenReturn(readings);
        when(ai.getRecommendation(any(), any())).thenReturn(Optional.of(new Advice("Shade and water.", List.of(SHADE, WATER))));

        var agreement = service.assessWithAI("farmer", 10L).agreement();
        assertThat(agreement.level()).isEqualTo(Level.HIGH);
        assertThat(agreement.matched()).containsExactlyInAnyOrder(SHADE, WATER);
    }

    private static List<EnvironmentReading> days(int count, double temperature, double sunshineHours) {
        return IntStream.range(0, count).boxed()
                .flatMap(i -> day(LocalDate.of(2026, 1, 1).plusDays(i), temperature, sunshineHours).stream()).toList();
    }

    private static List<EnvironmentReading> day(LocalDate date, double temperature, double sunshineHours) {
        return IntStream.range(0, 24).mapToObj(h -> new EnvironmentReading(
                date.atTime(h, 0), temperature, 0.25, 1.0, date, sunshineHours * 3600)).toList();
    }
}
