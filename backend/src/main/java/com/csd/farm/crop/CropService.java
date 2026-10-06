package com.csd.farm.crop;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
import com.csd.farm.crop.CropResponses.DashboardCrop;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Represents a service instance for processing data about a farmer's crops.
 */
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

    /**
     * Constructor for a CropService instance.
     * @param crops The crop repository to draw from.
     * @param farmers The farmer repository to draw from.
     * @param weatherReadings The weather repository to draw from.
     * @param health A service instance for checking crop health.
     * @param weatherService A service instance for checking the weather.
     */
    public CropService(CropRepository crops, FarmerRepository farmers,
                       CropWeatherRepository weatherReadings, CropHealthService health,
                       CropWeatherService weatherService) {
        this.crops = crops;
        this.farmers = farmers;
        this.weatherReadings = weatherReadings;
        this.health = health;
        this.weatherService = weatherService;
    }

    /**
     * Returns all available crop types as options.
     * 
     * @return The list of all possible options for crop types.
     */
    public List<CropOption> cropTypes() {
        return Arrays.stream(CropType.values())
                .map(type -> new CropOption(type.name(), type.displayName()))
                .toList();
    }

    /**
     * Returns all crop entries owned by a farmer.
     * 
     * @param username The farmer's username.
     */
    public List<CropEntry> list(String username) {
        return crops.findAllForFarmer(farmerId(username));
    }

    /**
     * Returns a Dashboard instance containing all relavant information about a farmer's crops
     * and the respective weather conditions alongside meaningful messages.
     * 
     * @param username The farmer's username.
     * @return A Dashboard instance storing all the information about the farmer's growing crops.
     */
    public Dashboard dashboard(String username) {
        Long owner = farmerId(username);
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

    /**
     * Creates a new CropEntry for a farmer.
     * 
     * @param username The farmer's username.
     * @param request The request for creating a crop.
     * @return The crop entry assigned to the farmer.
     */
    public CropEntry create(String username, CreateCropRequest request) {
        Long owner = farmerId(username);
        CropEntry entry = new CropEntry(request.cropType(), normalizeTime(request.plantedAt()),
                request.latitude(), request.longitude());
        crops.save(owner, entry);
        return entry;
    }

    /**
     * Returns the details of a farmer's growing crop.
     * 
     * @param username The farmer's username.
     * @param cropType The type of crop.
     * @param plantedAt Date and time when crop was planted.
     * @return Details about the crop.
     */
    public CropDetails details(String username, CropType cropType, OffsetDateTime plantedAt) {
        Long owner = farmerId(username);
        CropEntry entry = findCrop(owner, cropType, normalizeTime(plantedAt));
        return cropDetails(owner, entry);
    }

    /**
     * Manually updates the daily weather conditions for a farmer's crop.
     * @param username The farmer's username.
     * @param cropType The type of crop.
     * @param plantedAt Date and time when crop was planted.
     * @param weather The new daily weather conditions.
     * @return The new details about the crop.
     * @throws ResponseStatusException If automatic weather fetching via API is enabled or a newer
     * weather condition has already been updated.
     */
    public CropDetails updateWeather(String username, CropType cropType,
                                     OffsetDateTime plantedAt, DailyWeather weather) {
        Long owner = farmerId(username);
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

    /**
     * Returns all the information about an owner's crop entry as a DashboardCrop instance containing
     * all relavant information on the crop.
     * 
     * @param owner The Id of the farmer.
     * @param entry The relavant crop entry.
     * @return A DashboardCrop instance containing all relavant information.
     */
    private DashboardCrop dashboardCrop(Long owner, CropEntry entry) {
        var result = weatherService.load(owner, entry);
        DailyWeather weather = result.weather();
        var assessment = health.assess(entry.cropType(), weather);
        return new DashboardCrop(entry.cropType(), entry.plantedAt(), entry.latitude(), entry.longitude(),
                weather, assessment.readings(), assessment.needsAttention(), result.source(), result.message());
    }

    /**
     * Returns the details of a farmer's crop entry.
     * @param owner The Id of the farmer.
     * @param entry The relavant crop entry.
     * @return The relavant details about the crop.
     */
    private CropDetails cropDetails(Long owner, CropEntry entry) {
        var result = weatherService.load(owner, entry);
        DailyWeather weather = result.weather();
        var assessment = health.assess(entry.cropType(), weather);
        return new CropDetails(entry, null, assessment.readings(), assessment.recommendedActions(),
                DETAILS_NOTE, assessment.issues(), assessment.needsAttention(),
                weather, result.source(), result.message());
    }

    /**
     * Finds a crop entry for a farmer given the crop and the date and time planted.
     * @param owner The Id of the farmer.
     * @param cropType The type of crop.
     * @param plantedAt Date and time at which crop was planted.
     * @return The crop entry in question.
     * @throws ResponseStatusException When no crop entry could be found that matches.
     */
    private CropEntry findCrop(Long owner, CropType cropType, OffsetDateTime plantedAt) {
        return crops.findForFarmer(owner, cropType, plantedAt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop entry not found."));
    }

    /**
     * Takes a farmer's udername and finds the correpsonding Id.
     * @param username The farmer's username.
     * @return The farmer's Id.
     */
    private Long farmerId(String username) {
        // The controller supplies the authenticated username, never a farmer ID from the request body.
        return farmers.findByUsername(username).orElseThrow().id();
    }

    /**
     * Normalises a date and time to the correct offset and rounds to seconds.
     * @param time The date and time.
     * @return The normalised date and time.
     */
    private OffsetDateTime normalizeTime(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
