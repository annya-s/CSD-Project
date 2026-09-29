package com.csd.farm.crop;

import java.security.Principal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.CropHealthService.Reading;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CropController {

    private final CropRepository crops;
    private final FarmerRepository farmers;
    private final CropWeatherRepository weatherReadings;
    private final CropHealthService health;
    private final CropWeatherService weatherService;

    public CropController(CropRepository crops, FarmerRepository farmers,
                          CropWeatherRepository weatherReadings, CropHealthService health,
                          CropWeatherService weatherService) {
        this.crops = crops;
        this.farmers = farmers;
        this.weatherReadings = weatherReadings;
        this.health = health;
        this.weatherService = weatherService;
    }

    @GetMapping("/crop-types")
    public List<CropOption> cropTypes() {
        return Arrays.stream(CropType.values())
                .map(type -> new CropOption(type.name(), type.displayName()))
                .toList();
    }

    @GetMapping("/crops")
    public List<CropEntry> list(Principal principal) {
        return crops.findAllForFarmer(farmerId(principal));
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(Principal principal) {
        UUID owner = farmerId(principal);
        List<DashboardCrop> entries = crops.findAllForFarmer(owner).stream().map(entry -> {
            var result = weatherService.load(owner, entry);
            DailyWeather weather = result.weather();
            var assessment = health.assess(entry.cropType(), weather);
            return new DashboardCrop(entry.cropType(), entry.plantedAt(), entry.latitude(), entry.longitude(),
                    weather, assessment.readings(), assessment.needsAttention(), result.source(), result.message());
        }).toList();
        long attentionCount = entries.stream().filter(DashboardCrop::needsAttention).count();
        String entryLabel = entries.size() == 1 ? " crop entry." : " crop entries.";
        String summary = entries.isEmpty()
                ? "Add your first crop to start your farm overview."
                : "You have " + entries.size() + entryLabel + " " + attentionCount
                    + " flagged for review based on the available daily readings. Vapour pressure deficit and soil moisture are not assessed for now.";
        return new Dashboard(summary, entries);
    }

    @PostMapping("/crops")
    @ResponseStatus(HttpStatus.CREATED)
    public CropEntry create(Principal principal, @Valid @RequestBody CreateCropRequest request) {
        CropEntry entry = new CropEntry(request.cropType(), normalizeTime(request.plantedAt()),
                request.latitude(), request.longitude());
        crops.save(farmerId(principal), entry);
        return entry;
    }

    @GetMapping("/crops/{cropType}")
    public CropDetails details(
            Principal principal,
            @PathVariable CropType cropType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plantedAt) {
        UUID owner = farmerId(principal);
        CropEntry entry = crops.findForFarmer(owner, cropType, normalizeTime(plantedAt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop entry not found."));
        var result = weatherService.load(owner, entry);
        DailyWeather weather = result.weather();
        var assessment = health.assess(cropType, weather);
        return new CropDetails(entry, null, assessment.readings(), assessment.recommendedActions(),
                "These are preliminary comparisons of daily weather. "
                + "Automatic readings use today's full-day forecast in your timezone. "
                + "Sunlight bands are estimates, not damage limits. Vapour pressure deficit and soil moisture are not assessed yet. "
                + "The soil readings represent the 3–9 cm layer; their percentage is water by volume, not available water depleted.",
                assessment.issues(), assessment.needsAttention(), weather, result.source(), result.message());
    }

    @PostMapping("/crops/{cropType}/weather")
    public CropDetails updateWeather(
            Principal principal,
            @PathVariable CropType cropType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plantedAt,
            @Valid @RequestBody DailyWeather weather) {
        UUID owner = farmerId(principal);
        OffsetDateTime plantingTime = normalizeTime(plantedAt);
        crops.findForFarmer(owner, cropType, plantingTime)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop entry not found."));
        if (weatherService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Open-Meteo fetching is enabled. Manual weather updates are disabled.");
        }
        weather.validateCompletedDay();
        if (!weatherReadings.save(owner, cropType, plantingTime, weather)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer weather day is already saved for this crop.");
        }
        return details(principal, cropType, plantingTime);
    }

    private UUID farmerId(Principal principal) {
        // Never accept a farmer ID supplied by the browser for ownership decisions.
        return farmers.findByUsername(principal.getName()).orElseThrow().id();
    }

    private OffsetDateTime normalizeTime(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    public record CropOption(String code, String name) { }

    public record Dashboard(String summary, List<DashboardCrop> crops) { }

    public record DashboardCrop(CropType cropType, OffsetDateTime plantedAt,
                                BigDecimal latitude, BigDecimal longitude, DailyWeather weather,
                                List<Reading> readings, boolean needsAttention,
                                String weatherSource, String weatherMessage) { }

    public record CropDetails(CropEntry entry, Integer healthScore, List<Reading> readings,
                              List<String> recommendedActions, String note, List<String> issues,
                              boolean needsAttention, DailyWeather weather,
                              String weatherSource, String weatherMessage) { }
}
