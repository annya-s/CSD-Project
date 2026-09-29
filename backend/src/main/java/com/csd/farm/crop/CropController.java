package com.csd.farm.crop;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;

import com.csd.farm.crop.CropResponses.CropDetails;
import com.csd.farm.crop.CropResponses.CropOption;
import com.csd.farm.crop.CropResponses.Dashboard;
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

@RestController
@RequestMapping("/api")
public class CropController {

    private final CropService cropService;

    public CropController(CropService cropService) {
        this.cropService = cropService;
    }

    @GetMapping("/crop-types")
    public List<CropOption> cropTypes() {
        return cropService.cropTypes();
    }

    @GetMapping("/crops")
    public List<CropEntry> list(Principal principal) {
        return cropService.list(principal.getName());
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(Principal principal) {
        return cropService.dashboard(principal.getName());
    }

    @PostMapping("/crops")
    @ResponseStatus(HttpStatus.CREATED)
    public CropEntry create(Principal principal, @Valid @RequestBody CreateCropRequest request) {
        return cropService.create(principal.getName(), request);
    }

    @GetMapping("/crops/{cropType}")
    public CropDetails details(
            Principal principal,
            @PathVariable CropType cropType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plantedAt) {
        return cropService.details(principal.getName(), cropType, plantedAt);
    }

    @PostMapping("/crops/{cropType}/weather")
    public CropDetails updateWeather(
            Principal principal,
            @PathVariable CropType cropType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plantedAt,
            @Valid @RequestBody DailyWeather weather) {
        return cropService.updateWeather(principal.getName(), cropType, plantedAt, weather);
    }
}
