package com.csd.farm.crop;

import java.util.ArrayList;
import java.util.List;

import com.csd.farm.crop.CropConditions.Range;
import org.springframework.stereotype.Service;

@Service
public class CropHealthService {

    /**
     * Returns an assessment of a crop's health in the day's current weather conditions.
     * @param cropType The type of crop.
     * @param weather The weather conditions to compare to.
     * @return An Assessment instance representing the health of the crop.
     */
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
                new Reading("Vapour pressure deficit", weather == null ? null : weather.vapourPressureDeficitMeanKpa(),
                        "kPa", "NOT_ASSESSED", null),
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
            actions.add("Sunshine is above the reference band. Do not add shade solely because of this result; check temperature and the crop first. Longer sunshine alone is not harmful.");
        }
        if (temperature == null || sunshineHours == null) {
            actions.add("Daily weather values are missing or incomplete. Refresh the forecast before assessing temperature and sunlight.");
        }
        if (actions.isEmpty()) {
            actions.add("Continue monitoring. Temperature and sunlight are within their reference ranges; vapour pressure deficit and soil moisture remain unassessed.");
        }

        return new Assessment(readings, List.copyOf(issues), List.copyOf(actions), !issues.isEmpty());
    }

    /**
     * Returns a reading of whether a specific condition is within the range.
     * @param name Name of the reading.
     * @param value The value to compare to the range.
     * @param unit The unit of measurement.
     * @param range The range to be compared to.
     * @return A Reading instance representing a reading of whether the condition is within optimal range.
     */
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

    // Represents a reading of a crop harvest's specific condition in comparison to the optimum range.
    public record Reading(String name, Double value, String unit, String status, Range referenceRange) { }

    // Represents an assessment for a specific crop harvest, alongside how to rectify any issues.
    public record Assessment(List<Reading> readings, List<String> issues,
                             List<String> recommendedActions, boolean needsAttention) { }
}
