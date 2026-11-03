package com.csd.farm.crop;

// [ERD-ALIGNMENT CHANGE] Crop endpoints identify CROP_CYCLES rows by their primary key.
import java.security.Principal;
import java.util.List;
import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CropController {
    private final CropService cropService;
    public CropController(CropService cropService){this.cropService=cropService;}

    @GetMapping("/crop-types") public List<CropOption> cropTypes(){return cropService.cropTypes();}
    @GetMapping("/crops") public List<CropEntry> list(Principal p){return cropService.list(p.getName());}
    @GetMapping("/dashboard") public Dashboard dashboard(Principal p){return cropService.dashboard(p.getName());}
    @PostMapping("/crops") @ResponseStatus(HttpStatus.CREATED)
    public CropEntry create(Principal p,@Valid @RequestBody CreateCropRequest request){
        return cropService.create(p.getName(),request);
    }
    @GetMapping("/crops/{cropCycleId}")
    public CropDetails details(Principal p,@PathVariable Long cropCycleId){
        return cropService.details(p.getName(),cropCycleId);
    }
    // [ERD-ALIGNMENT ADDITION] Repository delete includes the logged-in user's ownership.
    @DeleteMapping("/crops/{cropCycleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal p,@PathVariable Long cropCycleId){
        cropService.delete(p.getName(),cropCycleId);
    }
    @PostMapping("/crops/{cropCycleId}/weather")
    public CropDetails updateWeather(Principal p,@PathVariable Long cropCycleId,
                                     @Valid @RequestBody DailyWeather weather){
        return cropService.updateWeather(p.getName(),cropCycleId,weather);
    }
}
