package com.csd.farm.crop.crop_category;

import java.util.ArrayList;
import java.util.List;

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
        if (aiOutput == null || aiOutput.isBlank() || totalWeightage == 0) {
            return null;
        }

        String temp = aiOutput.toLowerCase();

        double result = 0.0;

        for (Requirement req : requirements) {
            boolean pass = true;

            for (String str : req.getRequirement()) {
                if (!temp.contains(str.toLowerCase())) {
                    pass = false;
                    break;
                }
            }

            if (pass) {
                result += req.getWeightage();
            }
        }

        return result / totalWeightage;
    }
}
