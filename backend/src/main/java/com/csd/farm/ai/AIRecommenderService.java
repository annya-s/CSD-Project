package com.csd.farm.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.csd.farm.crop.CropType;
import com.csd.farm.environment.EnvironmentReading;
import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

@Service
public class AIRecommenderService {
    // private static final String key = "my_test_api_key"; // Used for manually testing a key
    // private static final Client client = Client.builder().apiKey(key).build();
    private static final Client client = new Client();
    private static final int maxTries = 5;


    public AIRecommenderService() {
        
    }

    public String getRecommendation(CropType cropType, List<EnvironmentReading> historicalReadings) {
        String historicalSummary = formatHistoricalReadings(historicalReadings);

        String prompt = formulatePrompt(cropType, historicalSummary);

        CreateModelInteraction params =
        CreateModelInteraction.builder()
            .model(Model.of("gemini-3.8-flash"))
            .input(InteractionsInput.of(prompt))
            .build();

        try {
            Interaction interaction = client.interactions.create(CreateInteractionRequestBody.of(params)).interaction().get();
            return interaction.outputText().orElse("No AI response");
        } catch (Exception e) {
            return "No AI Response";
        }
    }

    private String formulatePrompt(CropType cropType, String historicalSummary) {
        return """
                You are an agricultural decision-support assistant.

                The farmer is currently growing %s.

                Below are the historical weather readings for the
                last 14 complete days:

                %s

                Analyse the historical weather conditions and identify
                patterns that could affect the crop.

                Pay particular attention to:
                - Changes in temperature over the 14 days.
                - Whether soil moisture has been increasing or decreasing.
                - Whether sunshine exposure has been excessive.
                - Whether the conditions suggest temporary or sustained stress.

                Provide practical recommendations to help the farmer
                maintain healthy crop growth.

                There is no need to report back the readings that you have observed.
                The only thing you have to report back is the recommendation based on the readings that you have seen.

                Do not invent missing measurements.
                If information is unavailable, acknowledge it.
                Keep your recommendation within three sentences.
                """.formatted(cropType.displayName(), historicalSummary);
        }

    private String formatHistoricalReadings(List<EnvironmentReading> historicalReadings) {
        if (historicalReadings == null || historicalReadings.isEmpty()) {
            return "No historical weather readings available.";
        }

        Map<LocalDate, List<EnvironmentReading>> dailyReadings =
                new TreeMap<>();

        for (EnvironmentReading reading : historicalReadings) {
            if (reading == null || reading.date() == null) {
                continue;
            }

            dailyReadings.computeIfAbsent(
                    reading.date(),
                    date -> new ArrayList<>()
            ).add(reading);
        }

        StringBuilder summary = new StringBuilder();

        for (var entry : dailyReadings.entrySet()) {
            LocalDate date = entry.getKey();
            List<EnvironmentReading> readings = entry.getValue();

            List<Double> temperatures = readings.stream()
                    .map(EnvironmentReading::temperature)
                    .filter(Objects::nonNull)
                    .toList();

            List<Double> soilMoistures = readings.stream()
                    .map(EnvironmentReading::soilMoisture)
                    .filter(Objects::nonNull)
                    .toList();

            Double sunshineSeconds = readings.stream()
                    .map(EnvironmentReading::sunshineDuration)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Double minTemperature = temperatures.stream()
                    .mapToDouble(Double::doubleValue)
                    .min()
                    .isPresent()
                    ? temperatures.stream().mapToDouble(Double::doubleValue).min().getAsDouble()
                    : null;

            Double averageTemperature = temperatures.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .isPresent()
                    ? temperatures.stream().mapToDouble(Double::doubleValue).average().getAsDouble()
                    : null;

            Double maxTemperature = temperatures.stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .isPresent()
                    ? temperatures.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
                    : null;

            Double averageSoilMoisture = soilMoistures.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .isPresent()
                    ? soilMoistures.stream().mapToDouble(Double::doubleValue).average().getAsDouble()
                    : null;

            Double sunshineHours = sunshineSeconds == null
                    ? null : sunshineSeconds / 3600.0;

            summary.append("Date: ").append(date).append("\n");

            summary.append("Temperature (min/avg/max): ")
                    .append(formatValue(minTemperature))
                    .append(" / ")
                    .append(formatValue(averageTemperature))
                    .append(" / ")
                    .append(formatValue(maxTemperature))
                    .append(" °C\n");

            summary.append("Average soil moisture: ")
                    .append(formatValue(averageSoilMoisture))
                    .append(" m³/m³\n");

            summary.append("Sunshine duration: ")
                    .append(formatValue(sunshineHours))
                    .append(" hours\n\n");
        }

        if (summary.isEmpty()) {
            return "No valid historical weather readings available.";
        }

        return summary.toString();
    }

    private String formatValue(Double value) {
        return value == null
                ? "Unavailable"
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
