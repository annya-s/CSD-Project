package com.csd.farm.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.csd.farm.crop.CropType;
import com.csd.farm.environment.EnvironmentReading;
import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

import com.csd.farm.crop.crop_category.*;
import com.csd.farm.crop.CropHealthService.Reading;

@Service
public class AIRecommenderService {
    // private static final String key = "my_test_api_key"; // Used for manually testing a key
    // private static final Client client = Client.builder().apiKey(key).build();
    private static final Client client = new Client();


    public AIRecommenderService() {
        
    }


    public String getRecommendation(CropType cropType, List<EnvironmentReading> historicalReadings,
        EnvironmentReading latestReading, List<String> assessedStatuses
    ) {
        String historicalSummary = formatHistoricalReadings(historicalReadings);
        String latestSummary = formatHistoricalReadings(List.of(latestReading));

        String prompt = formulatePrompt(cropType, historicalSummary, latestSummary);
        Map<String, Object> schema = formulateSchema();
        CreateModelInteractionResponseFormat format = formulateFormat(schema);


        CreateModelInteraction params =
        CreateModelInteraction.builder()
            .model(Model.of("gemini-3.8-flash"))
            .input(InteractionsInput.of(prompt))
            .responseFormat(format)
            .build();

        try {
            Interaction interaction = client.interactions.create(CreateInteractionRequestBody.of(params)).interaction().get();

            String output = interaction.outputText().orElse("No AI response");
            List<String> stringOutputs = parseJson(output);

                assessedStatuses.add(stringOutputs.remove(0));
                assessedStatuses.add(stringOutputs.remove(0));
                
                return stringOutputs.remove(0);
        } catch (Exception e) {
            return "No AI Response";
        }
    }

    private String formulatePrompt(CropType cropType, String historicalSummary, String latestReading) {
        return """
                You are an agricultural decision-support assistant.

                %s

                First analyse the current weather conditions and assign an assessment
                of the crop's vapour pressure defecit and soil moisture.


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
                """.formatted(latestReading, cropType.displayName(), historicalSummary);
        }

        private Map<String, Object> formulateSchema() {
                Map<String, Object> properties = new HashMap<>();

                Map<String, Object> vpdProp = new HashMap<>();
                vpdProp.put("type", "string");
                vpdProp.put("enum", Arrays.asList("WITHIN_RANGE", "BELOW_RANGE",
                "BELOW_RANGE_SEVERE", "ABOVE_RANGE", "ABOVE_RANGE_SEVERE"));
                vpdProp.put("description", "The assessment of the crop's vapour pressure defecit");
                properties.put("vpd", vpdProp);

                Map<String, Object> soilMoistureProp = new HashMap<>();
                soilMoistureProp.put("type", "string");
                soilMoistureProp.put("enum", Arrays.asList("WITHIN_RANGE", "BELOW_RANGE",
                "BELOW_RANGE_SEVERE", "ABOVE_RANGE", "ABOVE_RANGE_SEVERE"));
                soilMoistureProp.put("description", "The assessment of the crop's soil moisture");
                properties.put("soil_moisture", soilMoistureProp);

                Map<String, Object> recommendationProp = new HashMap<>();
                recommendationProp.put("type", "string");
                recommendationProp.put("description", "The recommended actions that the farmer should take");
                properties.put("recommendation", recommendationProp);

                Map<String, Object> jsonSchema = new HashMap<>();
                jsonSchema.put("type", "object");
                jsonSchema.put("properties", properties);
                jsonSchema.put("required", Arrays.asList("vpd", "soil_moisture", "recommendation"));

                return jsonSchema;
        }

        private CreateModelInteractionResponseFormat formulateFormat(Map<String, Object> schema) {
                return CreateModelInteractionResponseFormat.of(
                ResponseFormat.of(
                TextResponseFormat.builder()
                .mimeType(TextResponseFormatMimeType.APPLICATION_JSON)
                .schema(schema)
                .build()));
        }

        private List<String> parseJson(String jsonString) {
                List<String> result = new ArrayList<>();
                
                JSONObject obj = (JSONObject) JSONValue.parse(jsonString);

                result.add((String) obj.get("vpd"));
                result.add((String) obj.get("soil_moisture"));
                result.add((String) obj.get("recommendation"));

                return result;
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
