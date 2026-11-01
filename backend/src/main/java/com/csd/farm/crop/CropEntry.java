package com.csd.farm.crop;

import java.time.OffsetDateTime;

// [ERD-ALIGNMENT CHANGE] API entry includes the ERD cycle key and farm context.
public record CropEntry(Long cropCycleId,String farmName,CropType cropType,
                        OffsetDateTime plantedAt,double latitude,double longitude){
    // [LEGACY COMPATIBILITY] Keeps older demo/test call sites source-compatible.
    public CropEntry(CropType cropType,OffsetDateTime plantedAt,double latitude,double longitude){
        this(null,"",cropType,plantedAt,latitude,longitude);
    }
}
