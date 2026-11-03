package com.csd.farm;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.csd.farm.crop.*;
import com.csd.farm.environment.EnvironmentReading;
import com.csd.farm.environment.EnvironmentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CropWeatherServiceTest {
    private final EnvironmentService environment = mock(EnvironmentService.class);
    private final CropWeatherRepository repository = mock(CropWeatherRepository.class);
    private final CropWeatherService service = new CropWeatherService(environment, repository, true);
    private final Long owner = 1L;
    private final CropEntry crop = new CropEntry(CropType.POTATO, OffsetDateTime.parse("2026-01-10T08:30:00Z"),
            1.0, 1.0);
    private final ZoneId zone = ZoneId.systemDefault();
    private final LocalDate today = LocalDate.now(zone);
    private final DailyWeather weather = new DailyWeather(today, zone.getId(),
            28.0, 25200.0, null, 0.25, 1.0);
    private final List<EnvironmentReading> hourlyReadings = IntStream.range(0, 24)
            .mapToObj(hour -> new EnvironmentReading(
                    today.atTime(hour, 0), 28.0, 0.25, 1.0, today, 25200.0))
            .toList();

    @Test
    void sharesApiCacheForTheSameCoordinatesButSavesEachOwnedPlanting() {
        when(environment.getAllReadings(1.0, 1.0)).thenReturn(hourlyReadings);
        var first = service.load(owner, crop);
        Long otherOwner = 2L;
        var second = service.load(otherOwner, crop);
        assertThat(first.source()).isEqualTo("OPEN_METEO");
        assertThat(second.weather()).isEqualTo(weather);
        verify(environment, times(1)).getAllReadings(1.0, 1.0);
        verify(repository).saveFromApi(owner, crop.cropType(), crop.plantedAt(), weather);
        verify(repository).saveFromApi(otherOwner, crop.cropType(), crop.plantedAt(), weather);
    }

    @Test
    void outageUsesOnlyPreviouslyVerifiedApiDataWithWarning() {
        when(environment.getAllReadings(1.0, 1.0)).thenThrow(new IllegalStateException("Unavailable"));
        when(repository.findFromApi(owner, crop.cropType(), crop.plantedAt())).thenReturn(Optional.of(weather));
        var result = service.load(owner, crop);
        assertThat(result.weather()).isEqualTo(weather);
        assertThat(result.message()).contains("previously saved API readings");
        verify(repository, never()).find(any(), any(), any());
    }

    @Test
    void outageWithoutApiHistoryDoesNotUseDemoReadingsAndRetriesAreLimited() {
        when(environment.getAllReadings(1.0, 1.0)).thenThrow(new IllegalStateException("Unavailable"));
        when(repository.find(owner, crop.cropType(), crop.plantedAt())).thenReturn(Optional.of(weather));
        var result = service.load(owner, crop);
        service.load(owner, crop);
        assertThat(result.weather()).isNull();
        assertThat(result.source()).isEqualTo("UNAVAILABLE");
        verify(repository, never()).find(any(), any(), any());
        verify(environment, times(1)).getAllReadings(1.0, 1.0);
    }
}
