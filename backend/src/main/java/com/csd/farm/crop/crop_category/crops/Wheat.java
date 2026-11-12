package com.csd.farm.crop.crop_category.crops;

import com.csd.farm.crop.CropType;
import com.csd.farm.crop.crop_category.Crop;
import com.csd.farm.crop.crop_category.CropRange;
import com.csd.farm.crop.crop_category.CropRequirements;
import com.csd.farm.crop.crop_category.Requirement;

import java.util.*;

public class Wheat extends Crop {
    public Wheat() {
        super.crop = CropType.WHEAT;
        super.temperatureRange = new CropRange(15, 23, 5, 27);
        super.sunshineRange = new CropRange(6, 8, 5, 9);
    }

    @Override
    public Double checkAction(String temperatureReading, String sunshineReading, String aiOutput) {
        CropRequirements reqs = new CropRequirements();

        switch(temperatureReading) {
            case "BELOW_RANGE_SEVERE":
                reqs.addRequirement(new Requirement(List.of("remove", "not sprouted"), 0.1));
                break;
            case "BELOW_RANGE":            
                break;
            case "ABOVE_RANGE":
                reqs.addRequirement(new Requirement(List.of("relocate"), 0.1));
                reqs.addRequirement(new Requirement(List.of("shade"), 0.1));
                reqs.addRequirement(new Requirement(List.of("water"), 0.1));
                break;
            case "ABOVE_RANGE_SEVERE":
                reqs.addRequirement(new Requirement(List.of("relocate", "protection"), 0.2));
                reqs.addRequirement(new Requirement(List.of("remove", "scorched"), 0.1));
                reqs.addRequirement(new Requirement(List.of("shade"), 0.1));
                reqs.addRequirement(new Requirement(List.of("water"), 0.2));
                break;
        }

        switch(sunshineReading) {
            case "BELOW_RANGE_SEVERE":
                reqs.addRequirement(new Requirement(List.of("relocate"), 0.2));
                reqs.addRequirement(new Requirement(List.of("artifical", "light"), 0.1));
                break;
            case "BELOW_RANGE":
                reqs.addRequirement(new Requirement(List.of("relocate"), 0.2));
                break;
            case "ABOVE_RANGE":
                reqs.addRequirement(new Requirement(List.of("relocate"), 0.1));
                break;
            case "ABOVE_RANGE_SEVERE":
                reqs.addRequirement(new Requirement(List.of("relocate"), 0.2));
                reqs.addRequirement(new Requirement(List.of("shade"), 0.2));
                break;
        }
        
        return reqs.confidenceInterval(aiOutput);
    }
}
