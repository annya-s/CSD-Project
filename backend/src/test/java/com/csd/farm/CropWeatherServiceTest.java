package com.csd.farm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.csd.farm.crop.*;
import com.csd.farm.environment.EnvironmentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CropWeatherServiceTest {
    private final EnvironmentService environment = mock(EnvironmentService.class);
    private final CropWeatherRepository repository = mock(CropWeatherRepository.class);
    private final CropWeatherService service = new CropWeatherService(environment, repository, true);
    private final UUID owner = UUID.randomUUID();
    private final CropEntry crop = new CropEntry(CropType.POTATO, OffsetDateTime.parse("2026-01-10T08:30:00Z"),
            BigDecimal.ONE, BigDecimal.ONE);
    private final DailyWeather weather = new DailyWeather(LocalDate.of(2026, 9, 27), "Asia/Singapore",
            28.0, 25200.0, 80.0, 0.3);

    @Test
    void sharesApiCacheForTheSameCoordinatesButSavesEachOwnedPlanting() {
        when(environment.getCompletedDay(1, 1)).thenReturn(weather);
        var first = service.load(owner, crop);
        var otherOwner = UUID.randomUUID();
        var second = service.load(otherOwner, crop);
        assertThat(first.source()).isEqualTo("OPEN_METEO");
        assertThat(second.weather()).isEqualTo(weather);
        verify(environment, times(1)).getCompletedDay(1, 1);
        verify(repository).saveFromApi(owner, crop.cropType(), crop.plantedAt(), weather);
        verify(repository).saveFromApi(otherOwner, crop.cropType(), crop.plantedAt(), weather);
    }

    @Test
    void outageUsesOnlyPreviouslyVerifiedApiDataWithWarning() {
        when(environment.getCompletedDay(1, 1)).thenThrow(new IllegalStateException("Unavailable"));
        when(repository.findFromApi(owner, crop.cropType(), crop.plantedAt())).thenReturn(Optional.of(weather));
        var result = service.load(owner, crop);
        assertThat(result.weather()).isEqualTo(weather);
        assertThat(result.message()).contains("previously saved API readings");
        verify(repository, never()).find(any(), any(), any());
    }

    @Test
    void outageWithoutApiHistoryDoesNotUseDemoReadingsAndRetriesAreLimited() {
        when(environment.getCompletedDay(1, 1)).thenThrow(new IllegalStateException("Unavailable"));
        when(repository.find(owner, crop.cropType(), crop.plantedAt())).thenReturn(Optional.of(weather));
        var result = service.load(owner, crop);
        service.load(owner, crop);
        assertThat(result.weather()).isNull();
        assertThat(result.source()).isEqualTo("UNAVAILABLE");
        verify(repository, never()).find(any(), any(), any());
        verify(environment, times(1)).getCompletedDay(1, 1);
    }
}
