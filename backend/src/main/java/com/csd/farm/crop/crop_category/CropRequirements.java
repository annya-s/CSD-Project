package com.csd.farm.crop.crop_category;

import java.util.*;

public class CropRequirements {
    private List<Requirement> requirements;
    private double totalWeightage;

    public CropRequirements() {
        this.requirements = new ArrayList<>();
    }

    public void addRequirement(Requirement req) {
        requirements.add(req);
        totalWeightage += req.getWeightage();
    }

    public Double confidenceInterval(String aiOutput) {
        String temp = aiOutput.toLowerCase();

        Double result = 0.0;
        for (Requirement req : requirements) {
            boolean pass = true;
            for (String str : req.getRequirement()) {
                if (!aiOutput.contains(str)) {
                    pass = false;
                    break;
                }
            }

            if (pass) {
                result += req.getWeightage() / totalWeightage;
            }
        }

        return result;
    }
}
