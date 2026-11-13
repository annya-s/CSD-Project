package com.csd.farm.crop;

import java.time.OffsetDateTime;
import java.util.List;

import com.csd.farm.crop.CropHealthService.Reading;

// [ERD-ALIGNMENT CHANGE] Dashboard includes Crop_Cycle_ID for detail and delete operations.
public final class CropResponses {
    private CropResponses(){}
    public record CropOption(String code,String name){}

    public record Dashboard(String summary,List<DashboardCrop> crops){}

    public record DashboardCrop(Long cropCycleId,String farmName,CropType cropType,
            OffsetDateTime plantedAt,double latitude,double longitude,DailyWeather weather,
            List<Reading> readings,boolean needsAttention,String weatherSource,String weatherMessage){}

    public record CropDetails(CropEntry entry,Integer healthScore,List<Reading> readings,
            List<String> recommendedActions,String note,List<String> issues,boolean needsAttention,
            DailyWeather weather,String weatherSource,String weatherMessage,Prediction prediction){}
    
    public record AIRecommendation(String recommendation, Double confidencePercent) {}
            
    public record Prediction(String status,Double value,String unit,String message,
            Double currentEstimate, Double referenceEstimate, String projectionDate, List<Cause> causes,
            YieldOutlook yieldOutlook){
        public Prediction(String status,Double value,String unit,String message){
            this(status,value,unit,message,null,null,null,List.of(),null);
        }
        Prediction withYield(YieldOutlook outlook){
            return new Prediction(status,value,unit,message,currentEstimate,referenceEstimate,
                    projectionDate,causes,outlook);
        }
    }

    /** Machine-learning yield estimate; ratio 1.0 means a normal year for the region. */
    public record YieldOutlook(String status, Double expectedYield, Double normalYield, Double ratio,
            String unit, String region, String model, String message){
        static YieldOutlook unavailable(){
            return new YieldOutlook("UNAVAILABLE",null,null,null,null,null,null,
                    "The yield model is temporarily unavailable.");
        }
    }

    /** One weather cause of the gap between the estimate and the full-growth target. */
    public record Cause(String factor, double actual, double optimal, String unit,
            double shortfallPercent, String direction){}
}
