package com.csd.farm.crop;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.csd.farm.auth.FarmerRepository;
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

    public CropController(CropRepository crops, FarmerRepository farmers) {
        this.crops = crops;
        this.farmers = farmers;
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
        List<CropEntry> entries = list(principal);
        String entryLabel = entries.size() == 1 ? " crop entry." : " crop entries.";
        String summary = entries.isEmpty()
                ? "Add your first crop to start your farm overview."
                : "You have " + entries.size() + entryLabel + " Health readings are not available yet.";
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
        CropEntry entry = crops.findForFarmer(farmerId(principal), cropType, normalizeTime(plantedAt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop entry not found."));

        // The mockups show sensor readings, but no sensor or AI service has been supplied yet.
        return new CropDetails(entry, null, List.of(
                new Reading("Water", null, "%", "NOT_MEASURED"),
                new Reading("Soil moisture", null, "%", "NOT_MEASURED"),
                new Reading("UV exposure", null, "UV index", "NOT_MEASURED"),
                new Reading("Fertilizer", null, null, "NOT_MEASURED"),
                new Reading("Soil pH", null, "pH", "NOT_MEASURED"),
                new Reading("Temperature", null, "°C", "NOT_MEASURED")),
                List.of("Record field measurements before making care decisions."),
                "Health, weather, crop photos, and chatbot services are not connected yet.");
    }

    private UUID farmerId(Principal principal) {
        // Never accept a farmer ID supplied by the browser for ownership decisions.
        return farmers.findByUsername(principal.getName()).orElseThrow().id();
    }

    private OffsetDateTime normalizeTime(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    public record CropOption(String code, String name) { }

    public record Dashboard(String summary, List<CropEntry> crops) { }

    public record Reading(String name, Double value, String unit, String status) { }

    public record CropDetails(CropEntry entry, Integer healthScore, List<Reading> readings,
                              List<String> recommendedActions, String note) { }
}
