package com.csd.farm.crop.crop_category;

import java.util.*;

public class Requirement {
    List<String> requirement;
    double weightage;

    public Requirement(List<String> requirement, double weightage) {
        this.requirement = new ArrayList<>();
        this.requirement.addAll(requirement);
        this.weightage = weightage;
    }

    public List<String> getRequirement() {
        return requirement;
    }

    public double getWeightage() {
        return weightage;
    }
}
