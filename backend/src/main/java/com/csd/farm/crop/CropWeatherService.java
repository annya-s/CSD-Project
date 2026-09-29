package com.csd.farm.crop;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.csd.farm.environment.EnvironmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CropWeatherService {
    private static final Logger log = LoggerFactory.getLogger(CropWeatherService.class);
    private final EnvironmentService environment;
    private final CropWeatherRepository readings;
    private final boolean enabled;
    private final Map<Location, CachedWeather> cache = new ConcurrentHashMap<>();

    public CropWeatherService(EnvironmentService environment, CropWeatherRepository readings,
                              @Value("${weather.api.enabled:true}") boolean enabled) {
        this.environment = environment;
        this.readings = readings;
        this.enabled = enabled;
    }

    public WeatherResult load(UUID owner, CropEntry crop) {
        if (!enabled) {
            return new WeatherResult(readings.find(owner, crop.cropType(), crop.plantedAt()).orElse(null),
                    "MANUAL", "Automatic weather fetching is disabled. Any supplied readings are manual.");
        }
        Location location = new Location(crop.latitude(), crop.longitude());
        // EnvironmentClient requests the server's timezone, so use the same calendar day here.
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        if (cache.size() > 1000) cache.clear();
        CachedWeather cached = cache.compute(location, (key, previous) -> {
            if (previous != null && Instant.now().isBefore(previous.expiresAt())
                    && (previous.weather() == null || previous.weather().date().equals(today))) return previous;
            try {
                var hourlyReadings = environment.getAllReadings(key.latitude(), key.longitude());
                DailyWeather weather = CropForecast.forDay(hourlyReadings, today, zone);
                return new CachedWeather(weather, Instant.now().plus(Duration.ofMinutes(15)));
            } catch (RuntimeException exception) {
                log.warn("Weather fetch failed: {}", exception.getClass().getSimpleName());
                // Briefly cache failures too, so page refreshes do not repeatedly hit a failing provider.
                return new CachedWeather(null, Instant.now().plus(Duration.ofMinutes(1)));
            }
        });
        if (cached.weather() != null) {
            readings.saveFromApi(owner, crop.cropType(), crop.plantedAt(), cached.weather());
            return new WeatherResult(cached.weather(), "OPEN_METEO",
                    "Today's full-day forecast averages and sunshine total, including forecast hours later today. "
                    + "The timezone is the server's timezone, as requested by the environment API.");
        }
        // Never fall back to the old demo/manual readings and call them real API data.
        DailyWeather saved = readings.findFromApi(owner, crop.cropType(), crop.plantedAt()).orElse(null);
        String message = saved == null
                ? "Weather could not be loaded from Open-Meteo. Please try again shortly."
                : "Open-Meteo could not be refreshed. Showing previously saved API readings; check their date. "
                  + "Saved readings may be forecasts. VPD is unavailable in saved snapshots.";
        return new WeatherResult(saved, saved == null ? "UNAVAILABLE" : "OPEN_METEO", message);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record WeatherResult(DailyWeather weather, String source, String message) { }
    private record Location(double latitude, double longitude) { }
    private record CachedWeather(DailyWeather weather, Instant expiresAt) { }
}
