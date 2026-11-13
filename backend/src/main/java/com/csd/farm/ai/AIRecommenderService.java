package com.csd.farm.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.csd.farm.crop.CropType;
import com.csd.farm.crop.crop_category.RecommendedAction;
import com.csd.farm.environment.EnvironmentReading;
import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AIRecommenderService {
    // private static final String key = "my_test_api_key"; // Used for manually testing a key
    // private static final Client client = Client.builder().apiKey(key).build();
    // Created on first use so the app and tests load without a Gemini API key.
    private Client client;
    private static final int maxTries = 5;
    private static final Logger log = LoggerFactory.getLogger(AIRecommenderService.class);
    private static final ObjectMapper json = new ObjectMapper();
    private static final List<String> ACTION_NAMES =
            java.util.Arrays.stream(RecommendedAction.values()).map(Enum::name).toList();
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "recommendation", Map.of("type", "string", "description", "At most three sentences."),
                    "actions", Map.of("type", "array", "items", Map.of("type", "string", "enum", ACTION_NAMES))),
            "required", List.of("recommendation", "actions"));

    /** The advisor's text plus the actions it prescribes, in the guideline vocabulary. */
    public record Advice(String recommendation, List<RecommendedAction> actions) {}


    public AIRecommenderService() {
        
    }

    /** Empty when Gemini fails or returns something that is not the requested JSON. */
    public Optional<Advice> getRecommendation(CropType cropType, List<EnvironmentReading> historicalReadings) {
        String historicalSummary = formatHistoricalReadings(historicalReadings);

        String prompt = formulatePrompt(cropType, historicalSummary);

        CreateModelInteraction params =
        CreateModelInteraction.builder()
            .model(Model.of("gemini-3.8-flash"))
            .input(InteractionsInput.of(prompt))
            .responseFormat(CreateModelInteractionResponseFormat.of(ResponseFormat.of(TextResponseFormat.builder()
                    .mimeType(TextResponseFormatMimeType.APPLICATION_JSON)
                    .schema(RESPONSE_SCHEMA)
                    .build())))
            .build();

        try {
            if (client == null) client = new Client();
            Interaction interaction = client.interactions.create(CreateInteractionRequestBody.of(params)).interaction().get();
            return interaction.outputText().flatMap(AIRecommenderService::parse);
        } catch (Exception e) {
            log.warn("AI recommendation failed ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Parses the advisor's JSON; unknown action names are dropped, anything malformed is no result. */
    public static Optional<Advice> parse(String output) {
        if (output == null || output.isBlank()) return Optional.empty();
        try {
            JsonNode root = json.readTree(output);
            JsonNode text = root.path("recommendation");
            if (!text.isString() || text.asString().isBlank()) return Optional.empty();
            List<RecommendedAction> actions = new ArrayList<>();
            for (JsonNode action : root.path("actions")) {
                if (ACTION_NAMES.contains(action.asString()) && !actions.contains(RecommendedAction.valueOf(action.asString())))
                    actions.add(RecommendedAction.valueOf(action.asString()));
            }
            return Optional.of(new Advice(text.asString().trim(), List.copyOf(actions)));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private String formulatePrompt(CropType cropType, String historicalSummary) {
        return """
                You are an agricultural decision-support assistant.

                The farmer is currently growing %s.

                Below are the historical weather readings for the last 14 complete days:

                %s

                Using the generally accepted optimal growing conditions for this crop, analyse the
                14 days and decide whether the crop has been under stress. Pay particular attention to:
                - Whether the mean temperature over the 14 days is below, within or above the crop's
                optimal range, and whether it has gone far outside it.
                - Whether the daily sunshine has been below, within or above what the crop needs.
                - Whether soil moisture has been increasing or decreasing.
                - Whether the stress is temporary (a few days) or sustained (most of the period).

                Choose actions only from this list, using these definitions:
                - WATER: temperature above the optimal range, or soil moisture falling.
                - REDUCE_WATER: soil moisture high or rising towards waterlogging.
                - SHADE: sunshine well above what the crop needs, or temperature far above the range.
                - PROTECT_FROM_COLD: temperature below the range (covers, fleece, greenhouse or windbreaks).
                - SUPPLEMENT_LIGHT: sunshine far below what the crop needs, and only if the crop can be lit.
                - RELOCATE: only for crops grown in pots or containers that can physically be moved.
                - REMOVE_DAMAGED: after severe heat or cold, remove scorched, frost-damaged or dead parts.
                - MONITOR: use alone when temperature and sunshine are within range and nothing needs to change.

                Rules:
                - Handle temperature and sunshine separately; if both are out of range, give actions for both.
                - If a reading is far outside the range, include REMOVE_DAMAGED where damage is likely.
                - Do not recommend an action that would make the stress worse
                (no SHADE when it is cold or dark, no PROTECT_FROM_COLD or REDUCE_WATER in heat,
                no SUPPLEMENT_LIGHT when sunshine is high).
                - "actions" must list every action your text recommends and nothing your text does not
                recommend. Do not list an action that your text advises against.

                There is no need to report back the readings that you have observed.
                The only thing you have to report back is the recommendation based on the readings that you have seen.

                Do not report back the readings. Do not invent missing measurements; if information
                is unavailable, say so in the recommendation.
                """.formatted(cropType.displayName(), historicalSummary, ACTION_NAMES);
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
