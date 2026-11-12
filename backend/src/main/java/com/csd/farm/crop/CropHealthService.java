package com.csd.farm.crop;

import java.util.ArrayList;
import java.util.List;

import com.csd.farm.crop.CropConditions.Range;
import com.csd.farm.ai.AIRecommenderService;
import com.csd.farm.crop.crop_category.*;
import org.springframework.stereotype.Service;

@Service
public class CropHealthService {

    /**
     * Returns an assessment of a crop's health in the day's current weather
     * conditions.
     * 
     * @param cropType The type of crop.
     * @param weather  The weather conditions to compare to.
     * @return An Assessment instance representing the health of the crop.
     */

    public Assessment assess(CropType cropType, DailyWeather weather) {
        var conditions = CropConditions.forCrop(cropType);
        if (conditions == null) {return new Assessment(new ArrayList<>(), List.of(),
                List.of("Reference growing conditions have not been configured for this crop."), false);
        }

        Double temperature = weather == null ? null : weather.temperatureMeanC();
        Double sunshineSeconds = weather == null ? null : weather.sunshineDurationSeconds();
        // Compare the unrounded number. Rounding is only for display in the browser.
        Double sunshineHours = sunshineSeconds == null ? null : sunshineSeconds / 3600.0;
        Double soilPercent = weather == null || weather.soilMoistureMeanM3M3() == null
                ? null
                : weather.soilMoistureMeanM3M3() * 100.0;

        Reading temperatureReading = findReading("Temperature", temperature, "°C", conditions.getTemperatureRange());
        Reading sunshineReading = findReading("Sunlight", sunshineHours, "hours/day", conditions.getSunshineRange());
        List<Reading> readings = List.of(temperatureReading,
                new Reading("Vapour pressure deficit", weather == null ? null : weather.vapourPressureDeficitMeanKpa(),
                        "kPa", "NOT_ASSESSED", null),
                new Reading("Soil moisture", soilPercent, "% by volume", "NOT_ASSESSED", null),
                sunshineReading);

        List<String> issues = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        String temperatureIssue = conditions.temperatureIssue(temperatureReading.status);
        String sunshineIssue = conditions.sunshineIssue(sunshineReading.status);

        if (temperatureIssue != null) {
            issues.add(temperatureIssue);
        }
        if (sunshineIssue != null) {
            issues.add(sunshineIssue);
        }

        if (temperature == null || sunshineHours == null) {
            actions.add(
                    "Daily weather values are missing or incomplete. Refresh the forecast before the agent can assess temperature and sunlight.");
            return new Assessment(readings, List.copyOf(issues), List.copyOf(actions), !issues.isEmpty());
        }

        AIRecommenderService ai = new AIRecommenderService();
        String aiOutput = ai.getRecommendation(temperature, sunshineHours, conditions);
        if (aiOutput == null || aiOutput.equals("No AI Response")) {
            actions.add(
                    "The agent could not formulate a response. Please contact an admin to investigate what went wrong.");
            return new Assessment(readings, List.copyOf(issues), List.copyOf(actions), !issues.isEmpty());
        }

        actions.add(aiOutput);

        double confidenceInterval = conditions.checkAction(temperatureReading.status, sunshineReading.status, aiOutput)
                * 100;
        String confidenceString = "Confidence Interval: ";
        confidenceString += String.format("%.2f", confidenceInterval);
        confidenceString += "%";

        actions.add(confidenceString);

        return new Assessment(readings, List.copyOf(issues), List.copyOf(actions), !issues.isEmpty());
    }

    /**
     * Returns a reading of whether a specific condition is within the range.
     * 
     * @param name  Name of the reading.
     * @param value The value to compare to the range.
     * @param unit  The unit of measurement.
     * @param range The range to be compared to.
     * @return A Reading instance representing a reading of whether the condition is
     *         within optimal range.
     */
    private Reading findReading(String name, Double value, String unit, CropRange range) {
        return new Reading(name, value, unit, range.getReading(value), new Range(range.getOptimalMinimum(), range.getOptimalMaximum()));
    }

    // Represents a reading of a crop harvest's specific condition in comparison to
    // the optimum range.
    public record Reading(String name, Double value, String unit, String status, Range referenceRange) {
    }

    // Represents an assessment for a specific crop harvest, alongside how to
    // rectify any issues.
    public record Assessment(List<Reading> readings, List<String> issues,
            List<String> recommendedActions, boolean needsAttention) {
    }
}
