package com.csd.farm.crop;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
import com.csd.farm.crop.CropResponses.DashboardCrop;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CropService {

    private static final String DETAILS_NOTE =
            "These are preliminary comparisons of daily weather. "
            + "Automatic readings use today's full-day forecast in your timezone. "
            + "Sunlight bands are estimates, not damage limits. Vapour pressure deficit and soil moisture are not assessed yet. "
            + "The soil readings represent the 3-9 cm layer; their percentage is water by volume, not available water depleted.";

    private final CropRepository crops;
    private final FarmerRepository farmers;
    private final CropWeatherRepository weatherReadings;
    private final CropHealthService health;
    private final CropWeatherService weatherService;

    public CropService(CropRepository crops, FarmerRepository farmers,
                       CropWeatherRepository weatherReadings, CropHealthService health,
                       CropWeatherService weatherService) {
        this.crops = crops;
        this.farmers = farmers;
        this.weatherReadings = weatherReadings;
        this.health = health;
        this.weatherService = weatherService;
    }

    public List<CropOption> cropTypes() {
        return Arrays.stream(CropType.values())
                .map(type -> new CropOption(type.name(), type.displayName()))
                .toList();
    }

    public List<CropEntry> list(String username) {
        return crops.findAllForFarmer(farmerId(username));
    }

    public Dashboard dashboard(String username) {
        UUID owner = farmerId(username);
        List<DashboardCrop> entries = crops.findAllForFarmer(owner).stream()
                .map(entry -> dashboardCrop(owner, entry))
                .toList();
        long attentionCount = entries.stream().filter(DashboardCrop::needsAttention).count();
        String entryLabel = entries.size() == 1 ? " crop entry." : " crop entries.";
        String summary = entries.isEmpty()
                ? "Add your first crop to start your farm overview."
                : "You have " + entries.size() + entryLabel + " " + attentionCount
                    + " flagged for review based on the available daily readings. Vapour pressure deficit and soil moisture are not assessed for now.";
        return new Dashboard(summary, entries);
    }

    public CropEntry create(String username, CreateCropRequest request) {
        UUID owner = farmerId(username);
        CropEntry entry = new CropEntry(request.cropType(), normalizeTime(request.plantedAt()),
                request.latitude(), request.longitude());
        crops.save(owner, entry);
        return entry;
    }

    public CropDetails details(String username, CropType cropType, OffsetDateTime plantedAt) {
        UUID owner = farmerId(username);
        CropEntry entry = findCrop(owner, cropType, normalizeTime(plantedAt));
        return cropDetails(owner, entry);
    }

    public CropDetails updateWeather(String username, CropType cropType,
                                     OffsetDateTime plantedAt, DailyWeather weather) {
        UUID owner = farmerId(username);
        OffsetDateTime plantingTime = normalizeTime(plantedAt);
        CropEntry entry = findCrop(owner, cropType, plantingTime);
        if (weatherService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Open-Meteo fetching is enabled. Manual weather updates are disabled.");
        }
        weather.validateCompletedDay();
        if (!weatherReadings.save(owner, cropType, plantingTime, weather)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A newer weather day is already saved for this crop.");
        }
        return cropDetails(owner, entry);
    }

    private DashboardCrop dashboardCrop(UUID owner, CropEntry entry) {
        var result = weatherService.load(owner, entry);
        DailyWeather weather = result.weather();
        var assessment = health.assess(entry.cropType(), weather);
        return new DashboardCrop(entry.cropType(), entry.plantedAt(), entry.latitude(), entry.longitude(),
                weather, assessment.readings(), assessment.needsAttention(), result.source(), result.message());
    }

    private CropDetails cropDetails(UUID owner, CropEntry entry) {
        var result = weatherService.load(owner, entry);
        DailyWeather weather = result.weather();
        var assessment = health.assess(entry.cropType(), weather);
        return new CropDetails(entry, null, assessment.readings(), assessment.recommendedActions(),
                DETAILS_NOTE, assessment.issues(), assessment.needsAttention(),
                weather, result.source(), result.message());
    }

    private CropEntry findCrop(UUID owner, CropType cropType, OffsetDateTime plantedAt) {
        return crops.findForFarmer(owner, cropType, plantedAt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop entry not found."));
    }

    private UUID farmerId(String username) {
        // The controller supplies the authenticated username, never a farmer ID from the request body.
        return farmers.findByUsername(username).orElseThrow().id();
    }

    private OffsetDateTime normalizeTime(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
