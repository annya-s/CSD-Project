package com.csd.farm.ai;

import java.util.*;

import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

import com.csd.farm.crop.crop_category.*;
import com.csd.farm.crop.CropHealthService.Reading;

public class AIRecommenderService {
    // private static final String key = "my_test_api_key"; // Used for manually testing a key
    // private static final Client client = Client.builder().apiKey(key).build();
    private static final Client client = new Client();


    public AIRecommenderService() {
        
    }

    public String getRecommendation(Double temperature, Double sunshineHours, 
        Reading vpd, Reading soilMoisture, Crop crop) {
        Double vpdNum = vpd.value();
        Double soilMoistureNum = soilMoisture.value();

        String prompt = formulatePrompt(temperature, sunshineHours, vpdNum, soilMoistureNum, crop);

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> vpdProp = new HashMap<>();
        vpdProp.put("type", "string");
        vpdProp.put("description", "The assessment of the crop's vpd");
        properties.put("vpd", properties);

        Map<String, Object> soilMoistureProp = new HashMap<>();
        soilMoistureProp.put("type", "string");
        soilMoistureProp.put("description", "The assessment of the crop's soil moisture");
        properties.put("soil_moisture", properties);

        Map<String, Object> recommendationProp = new HashMap<>();
        recommendationProp.put("type", "string");
        recommendationProp.put("description", "The recommended actions for the farmer");
        properties.put("recommendation", properties);

        Map<String, Object> jsonSchema = new HashMap<>();
        jsonSchema.put("type", "object");
        jsonSchema.put("properties", properties);
        jsonSchema.put("required", Arrays.asList("vpd", "soil_moisture", "recommendation"));

        CreateModelInteraction params =
        CreateModelInteraction.builder()
            .model(Model.of("gemini-3.8-flash"))
            .input(InteractionsInput.of(prompt))
            .responseFormat();
            .build();

        Interaction interaction = null;
        try {
            interaction = client.interactions.create(CreateInteractionRequestBody.of(params)).interaction().get();
        } catch (Exception e) {
            return "No AI Response";
        }

        return interaction.outputText().orElse("No AI Response");
    }

    public String formulatePrompt(Double temperature, Double sunshineHours, 
        Double vpd, Double soilMoisture, Crop crop) {
        StringBuilder sb = new StringBuilder("There is a ");
        sb.append(crop.getCrop().displayName());
        sb.append(" crop growing currently with a temperature of ");
        sb.append(temperature.toString());
        sb.append(" °C and ");
        sb.append(sunshineHours.toString());
        sb.append(" hours exposed to sunlight. ");
        sb.append("Give a detailed recommendation on what the farmer should do to maintain good health on this crop. ");
        sb.append("If the crop is in fine health, simply state that it is alright and no further action is needed. ");
        sb.append("Keep the response concise and not exceeding three sentences.");

        sb.append("Additionally, the plant has a vapour pressure deficit of ");
        sb.append(vpd.toString());
        sb.append(" and a soil moisture of ")
        sb.append(soilMoisture.toString());
        sb.append(". Please give these 2 measurements an assessment from a set of possible assessments.");
        sb.append("The set of possible assessments are BELOW_RANGE_SEVERE, BELOW_RANGE, WITHIN_RANGE, ABOVE_RANGE and ABOVE_RANGE_SEVERE.");
        sb.append("");

        return sb.toString();
    }
}
