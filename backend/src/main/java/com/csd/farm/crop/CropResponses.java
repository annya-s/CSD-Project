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
            DailyWeather weather,String weatherSource,String weatherMessage){}
}
