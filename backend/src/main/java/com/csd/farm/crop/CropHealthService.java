package com.csd.farm.crop;

import java.util.ArrayList;
import java.util.List;

import com.csd.farm.crop.CropConditions.Range;
import org.springframework.stereotype.Service;

@Service
public class CropHealthService {

    public Assessment assess(CropType cropType, DailyWeather weather) {
        var conditions = CropConditions.forCrop(cropType);
        Double temperature = weather == null ? null : weather.temperatureMeanC();
        Double sunshineSeconds = weather == null ? null : weather.sunshineDurationSeconds();
        // Compare the unrounded number. Rounding is only for display in the browser.
        Double sunshineHours = sunshineSeconds == null ? null : sunshineSeconds / 3600.0;
        Double soilPercent = weather == null || weather.soilMoistureMeanM3M3() == null
                ? null : weather.soilMoistureMeanM3M3() * 100.0;

        Reading temperatureReading = compare("Temperature", temperature, "°C", conditions.temperatureC());
        Reading sunshineReading = compare("Sunlight", sunshineHours, "hours/day", conditions.sunshineHours());
        List<Reading> readings = List.of(
                temperatureReading,
                new Reading("Humidity", weather == null ? null : weather.humidityMeanPercent(), "%", "NOT_ASSESSED", null),
                new Reading("Soil moisture", soilPercent, "% by volume", "NOT_ASSESSED", null),
                sunshineReading);

        List<String> issues = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        if (temperatureReading.status().equals("BELOW_RANGE")) {
            issues.add("Daily mean temperature is below this crop's reference range.");
            actions.add("Check temperatures at the crop and inspect for cold stress. Consider suitable protection if cold conditions persist.");
        } else if (temperatureReading.status().equals("ABOVE_RANGE")) {
            issues.add("Daily mean temperature is above this crop's reference range.");
            actions.add("Check temperatures at the crop and inspect for heat stress. Check soil water before deciding whether to irrigate.");
        }
        if (sunshineReading.status().equals("BELOW_RANGE")) {
            issues.add("Daily sunshine is below this crop's estimated reference range.");
            actions.add("Check for shading and monitor sunshine over the next few days. One cloudy day does not establish crop damage.");
        } else if (sunshineReading.status().equals("ABOVE_RANGE")) {
            // Above the estimated band is informational; longer sunshine alone is not harmful.
            actions.add("Sunshine is above the reference band. Do not add shade solely because of this result; check temperature and the crop first.");
        }
        if (temperature == null || sunshineHours == null) {
            actions.add("Supply the missing completed-day weather readings to assess temperature and sunlight.");
        }
        if (actions.isEmpty()) {
            actions.add("Continue monitoring. Temperature and sunlight are within their reference ranges; humidity and soil moisture remain unassessed.");
        }

        return new Assessment(readings, List.copyOf(issues), List.copyOf(actions), !issues.isEmpty());
    }

    private Reading compare(String name, Double value, String unit, Range range) {
        String status = "NOT_ASSESSED";
        if (value != null) {
            if (value < range.minimum()) {
                status = "BELOW_RANGE";
            } else if (value > range.maximum()) {
                status = "ABOVE_RANGE";
            } else {
                status = "WITHIN_RANGE";
            }
        }
        return new Reading(name, value, unit, status, range);
    }

    public record Reading(String name, Double value, String unit, String status, Range referenceRange) { }

    public record Assessment(List<Reading> readings, List<String> issues,
                             List<String> recommendedActions, boolean needsAttention) { }
}
