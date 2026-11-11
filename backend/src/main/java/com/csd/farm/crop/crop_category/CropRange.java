package com.csd.farm.crop.crop_category;

public class CropRange {
    private double optimalMinimum;
    private double optimalMaximum;

    private double absMinimum;
    private double absMaximum;

    public CropRange(double optimalMinimum, double optimalMaximum, double absMinimum, double absMaximum) {
        this.optimalMinimum = optimalMinimum;
        this.optimalMaximum = optimalMaximum;
        this.absMinimum = absMinimum;
        this.absMaximum = absMaximum;
    }

    public String getReading(Double data) {
        if (data == null) {
            return "NOT_ASSESSED";
        }

        if (data < optimalMinimum) {
            if (data < absMinimum) {
                return "BELOW_RANGE_SEVERE";
            } else {
                return "BELOW_RANGE";
            }
        } else if (data > optimalMaximum) {
            if (data > absMaximum) {
                return "ABOVE_RANGE_SEVERE";
            } else {
                return "ABOVE_RANGE";
            }
        }

        return "WITHIN_RANGE";
    }

    public double getOptimalMinimum() {
        return optimalMinimum;
    }

    public double getOptimalMaximum() {
        return optimalMaximum;
    }
}
