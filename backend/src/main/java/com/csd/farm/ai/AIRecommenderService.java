package com.csd.farm.ai;

import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

import com.csd.farm.crop.crop_category.*;

public class AIRecommenderService {
    // private static final String key = "my_test_api_key"; // Used for manually testing a key
    // private static final Client client = Client.builder().apiKey(key).build();
    private static final Client client = new Client();
    private static final int maxTries = 5;


    public AIRecommenderService() {
        
    }

    public String getRecommendation(Double temperature, Double sunshineHours, Crop crop) {
        String prompt = formulatePrompt(temperature, sunshineHours, crop);

        CreateModelInteraction params =
        CreateModelInteraction.builder()
            .model(Model.of("gemini-3.8-flash"))
            .input(InteractionsInput.of(prompt))
            .build();

        try {
            Interaction interaction = client.interactions.create(CreateInteractionRequestBody.of(params)).interaction().get();
            return interaction.outputText().orElse("No AI Response");
        } catch (Exception e) {
            return "No AI Response";
        }
    }

    public String formulatePrompt(Double temperature, Double sunshineHours, Crop crop) {
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

        return sb.toString();
    }
}
