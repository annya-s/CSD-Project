package com.csd.farm.crop;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
import com.csd.farm.crop.CropResponses.DashboardCrop;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public CropService(CropRepository crops,CropTypeRepository cropTypes,FarmerRepository farmers,FarmRepository farms,
            CropWeatherRepository weatherReadings,CropHealthService health,CropWeatherService weatherService,
            MLPredictionService predictions){
        this.crops=crops;this.cropTypes=cropTypes;this.farmers=farmers;this.farms=farms;
        this.weatherReadings=weatherReadings;this.health=health;this.weatherService=weatherService;
        this.predictions=predictions;
    }
    CropResponses.Prediction synthetic(CropType crop, int age, String scenario){
        return predictions.synthetic(crop, age, scenario);
    }

    CropResponses.Prediction sampleWheat(){return predictions.sampleWheat();}

    public List<CropOption> cropTypes(){return cropTypes.findAll();}
    public List<CropEntry> list(String username){return crops.findAllForFarmer(userId(username));}
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
    public CropDetails updateWeather(String username,Long cycleId,DailyWeather weather){
        Long owner=userId(username);CropEntry entry=findCrop(owner,cycleId);
        if(weatherService.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Open-Meteo fetching is enabled. Manual weather updates are disabled.");
        weather.validateCompletedDay();
        if(!weatherReadings.save(entry.cropCycleId(),weather))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A newer weather day is already saved for this crop.");
        return cropDetails(owner,entry);
    }
    private DashboardCrop dashboardCrop(Long owner,CropEntry entry){
        var result=weatherService.load(owner,entry);DailyWeather w=result.weather();
        var assessment=health.assess(entry.cropType(),w);
        return new DashboardCrop(entry.cropCycleId(),entry.farmName(),entry.cropType(),entry.plantedAt(),
            entry.latitude(),entry.longitude(),w,assessment.readings(),assessment.needsAttention(),result.source(),result.message());
    }
    private CropDetails cropDetails(Long owner,CropEntry entry){
        var result=weatherService.load(owner,entry);DailyWeather w=result.weather();
        var assessment=health.assess(entry.cropType(),w);
        var prediction=predictions.forPlanting(entry);
        return new CropDetails(entry,null,assessment.readings(),assessment.recommendedActions(),
            DETAILS_NOTE+" "+prediction.message(),assessment.issues(),assessment.needsAttention(),
            w,result.source(),result.message(),prediction);
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
