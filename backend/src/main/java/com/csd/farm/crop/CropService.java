package com.csd.farm.crop;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.csd.farm.ai.AIRecommenderService;
import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
import com.csd.farm.crop.CropResponses.DashboardCrop;
import com.csd.farm.environment.EnvironmentService;

@Service
public class CropService {
    private static final String DETAILS_NOTE="Preliminary daily weather comparisons; recommendations are rules-based and not a complete crop diagnosis.";
    private final CropRepository crops;
    private final CropTypeRepository cropTypes;
    private final FarmerRepository farmers;
    private final FarmRepository farms;
    private final CropWeatherRepository weatherReadings;
    private final CropHealthService health;
    private final CropWeatherService weatherService;
    private final MLPredictionService predictions;
    private final EnvironmentService environmentService;
    private final JdbcClient jdbc;
    private final AIRecommenderService aiRecommender;
    // Re-fetch once per application run so old, untagged soil-depth rows are not reused.
    private final java.util.Set<Long> refreshedSoilDepth = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CropService(CropRepository crops,CropTypeRepository cropTypes,FarmerRepository farmers,FarmRepository farms,
            CropWeatherRepository weatherReadings,CropHealthService health,CropWeatherService weatherService, 
            MLPredictionService predictions,EnvironmentService environmentService,JdbcClient jdbc, AIRecommenderService aiRecommender){
        this.crops=crops;
        this.cropTypes=cropTypes;
        this.farmers=farmers;
        this.farms=farms;
        this.weatherReadings=weatherReadings;
        this.health=health;
        this.weatherService=weatherService;
        this.predictions=predictions;
        this.environmentService=environmentService;
        this.jdbc=jdbc;
        this.aiRecommender=aiRecommender;
    }
    CropResponses.Prediction synthetic(CropType crop, int age, String scenario){
        return predictions.synthetic(crop, age, scenario);
    }

    public List<CropOption> cropTypes(){
        return cropTypes.findAll();
    }

    public List<CropEntry> list(String username){
        return crops.findAllForFarmer(userId(username));
    }

    public Dashboard dashboard(String username){
        Long owner=userId(username);
        List<DashboardCrop> entries=crops.findAllForFarmer(owner).stream().map(e->dashboardCrop(owner,e)).toList();
        long flagged=entries.stream().filter(DashboardCrop::needsAttention).count();
        String summary=entries.isEmpty()?"Add your first crop to start your farm overview."
            :"You have "+entries.size()+" crop entries. "+flagged+" flagged for review.";
        return new Dashboard(summary,entries);
    }
    public CropEntry create(String username,CreateCropRequest request){
        String farmName=request.farmName()==null||request.farmName().isBlank()
            ?"Main Farm":request.farmName().trim();
        CropEntry entry=new CropEntry(null,farmName,request.cropType(),
            normalizeTime(request.plantedAt()),request.latitude(),request.longitude());
        return crops.save(userId(username),entry);
    }
    @Transactional
    public void delete(String username,Long cycleId){
        Long owner=userId(username);
        Long farmId=crops.findFarmIdForCrop(owner,cycleId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Crop entry not found.")).longValue();
        if(!crops.deleteForFarmer(owner,cycleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Crop entry not found.");
        if(crops.countForFarm(farmId)==0) farms.deleteForUser(owner,farmId);
    }

    public CropDetails details(String username,Long cycleId){
        Long owner=userId(username);return cropDetails(owner,findCrop(owner,cycleId));
    }

    public CropResponses.Prediction predict(String username,Long cycleId){
        CropEntry entry=findCrop(userId(username),cycleId);
        refreshHistoricalAverages(entry);
        return predictions.forPlanting(entry);
    }
    
    public CropDetails updateWeather(String username,Long cycleId,DailyWeather weather){
        Long owner=userId(username);CropEntry entry=findCrop(owner,cycleId);
        if(weatherService.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Open-Meteo fetching is enabled. Manual weather updates are disabled.");
        weather.validateCompletedDay();
        if(!weatherReadings.save(entry.cropCycleId(),weather))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A newer weather day is already saved for this crop.");
        return cropDetails(owner,entry);
    }

    public CropResponses.AIRecommendation assessWithAI(String username, Long cycleId) {
        CropEntry entry = findCrop(userId(username), cycleId);
        DailyWeather weather = weatherReadings.find(cycleId).orElse(null);

        CropHealthService.Assessment assessment = health.assess(entry.cropType(), weather);

        String recommendation = aiRecommender.getRecommendation(
                entry.cropType(),
                weather == null ? null : weather.temperatureMeanC(),
                weather == null ? null : weather.soilMoistureMeanM3M3(),
                weather == null ? null : weather.sunshineDurationSeconds()
        );

        Double confidencePercent = null;
        var conditions = CropConditions.forCrop(entry.cropType());

        if (conditions != null && recommendation != null && !recommendation.equals("No AI Response")) {
            String temperatureStatus = assessment.readings().stream()
                    .filter(r -> r.name().equals("Temperature"))
                    .map(CropHealthService.Reading::status)
                    .findFirst()
                    .orElse("NOT_ASSESSED");

            String sunshineStatus = assessment.readings().stream()
                    .filter(r -> r.name().equals("Sunlight"))
                    .map(CropHealthService.Reading::status)
                    .findFirst()
                    .orElse("NOT_ASSESSED");

            Double confidence = conditions.checkAction(
                    temperatureStatus,
                    sunshineStatus,
                    recommendation
            );

            confidencePercent = confidence == null ? null : confidence * 100;
        }

        return new CropResponses.AIRecommendation(recommendation, confidencePercent);
    }

    private DashboardCrop dashboardCrop(Long owner,CropEntry entry){
        var result=weatherService.load(owner,entry);DailyWeather w=result.weather();
        var assessment=health.assess(entry.cropType(),w);
        return new DashboardCrop(entry.cropCycleId(),entry.farmName(),entry.cropType(),entry.plantedAt(),
            entry.latitude(),entry.longitude(),result.weather(),assessment.readings(),assessment.needsAttention(),result.source(),result.message());
    }
    private CropDetails cropDetails(Long owner,CropEntry entry){
        // Page loading must never wait for Open-Meteo or a Python process.
        DailyWeather w=weatherReadings.find(entry.cropCycleId()).orElse(null);
        var assessment=health.assess(entry.cropType(),w);
        var prediction=new CropResponses.Prediction("NOT_RUN",null,null,
                "Press Run biomass prediction to calculate the estimate and target.");
        return new CropDetails(entry,null,assessment.readings(),assessment.recommendedActions(),
            DETAILS_NOTE,assessment.issues(),assessment.needsAttention(),
            w,w==null?"UNAVAILABLE":"SAVED",
            w==null?"No saved weather readings yet.":"Saved weather readings; check their date. Predictions use separate 14-day averages.",prediction);
    }
    private String refreshHistoricalAverages(CropEntry entry){
        // Respect offline/manual-weather mode. Existing saved averages can still be used.
        if(!weatherService.isEnabled()) return "";
        try {
            LocalDate today=LocalDate.now(ZoneId.systemDefault());
            boolean current=jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM public."CROP_WEATHER_HISTORICAL_AVERAGE"
                    WHERE "Crop_Cycle_ID" = :cycle
                      AND "Start_Date" = :start AND "End_Date" = :end
                      AND "Temperature_Mean" IS NOT NULL
                      AND "Soil_Moisture_Mean" IS NOT NULL
                      AND "Vapour_Pressure_Deficit_Mean" IS NOT NULL
                      AND "Sunshine_Duration_Mean_Seconds" IS NOT NULL
                )
                """).param("cycle",entry.cropCycleId()).param("start",today.minusDays(14))
                    .param("end",today.minusDays(1)).query(Boolean.class).single();
            if(!current || !refreshedSoilDepth.contains(entry.cropCycleId())) {
                environmentService.storeAverageHistoricalReading(Math.toIntExact(entry.cropCycleId()));
                refreshedSoilDepth.add(entry.cropCycleId());
            }
            return "";
        } catch(RuntimeException failure){
            // Keep the page and synthetic target available if the API or database fails.
            org.slf4j.LoggerFactory.getLogger(CropService.class).warn(
                "Historical weather refresh failed for crop cycle {} ({})",
                entry.cropCycleId(),failure.getClass().getSimpleName());
            return " Could not refresh the 14-day weather averages. Reopen this page to retry.";
        }
    }
    private CropEntry findCrop(Long owner,Long cycleId){
        return crops.findForFarmer(owner,cycleId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Crop entry not found."));
    }
    private Long userId(String username){return farmers.findByUsername(username).orElseThrow().id();}
    private OffsetDateTime normalizeTime(OffsetDateTime time){
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
