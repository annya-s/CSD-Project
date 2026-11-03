package com.csd.farm.crop;

// [ERD-ALIGNMENT ADDITION] The ERD has no forecast snapshot columns, so weather uses a
// one-to-one support table keyed by public.CROP_CYCLES.Crop_Cycle_ID.
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CropWeatherRepository {
    private final JdbcClient jdbc;
    public CropWeatherRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    public Optional<DailyWeather> find(Long cycleId){
        return jdbc.sql("""
            SELECT "Weather_Date","Weather_Timezone","Temperature_Mean_C",
                   "Sunshine_Duration_Seconds","Humidity_Mean_Percent","Soil_Moisture_Mean_M3M3"
            FROM public."CROP_WEATHER_READINGS" WHERE "Crop_Cycle_ID"=:id
            """).param("id",cycleId).query((r,n)->new DailyWeather(
                r.getObject("Weather_Date",LocalDate.class),r.getString("Weather_Timezone"),
                r.getObject("Temperature_Mean_C",Double.class),r.getObject("Sunshine_Duration_Seconds",Double.class),
                r.getObject("Humidity_Mean_Percent",Double.class),r.getObject("Soil_Moisture_Mean_M3M3",Double.class)))
            .optional();
    }
    public boolean save(Long cycleId,DailyWeather weather){return save(cycleId,weather,"MANUAL");}
    public boolean saveFromApi(Long cycleId,DailyWeather weather){return save(cycleId,weather,"OPEN_METEO");}
    public Optional<DailyWeather> findFromApi(Long cycleId){
        String source=jdbc.sql("""
            SELECT "Weather_Source" FROM public."CROP_WEATHER_READINGS" WHERE "Crop_Cycle_ID"=:id
            """).param("id",cycleId).query(String.class).optional().orElse("");
        return source.equals("OPEN_METEO")?find(cycleId):Optional.empty();
    }
    // [LEGACY LOCAL STORAGE] Old H2 test/demo repository API; Supabase requests use cycle IDs.
    @Deprecated
    public Optional<DailyWeather> find(Long farmerId,CropType type,java.time.OffsetDateTime plantedAt){
        return jdbc.sql("""
            SELECT weather_date,weather_timezone,temperature_mean_c,sunshine_duration_seconds,
                   humidity_mean_percent,soil_moisture_mean_m3m3
            FROM farm.crop_entry
            WHERE farmer_id=:owner AND crop_type=:type AND planted_at=:planted
              AND weather_date IS NOT NULL
            """).param("owner",farmerId).param("type",type.name()).param("planted",plantedAt)
            .query((r,n)->new DailyWeather(r.getObject("weather_date",LocalDate.class),r.getString("weather_timezone"),
                r.getObject("temperature_mean_c",Double.class),r.getObject("sunshine_duration_seconds",Double.class),
                r.getObject("humidity_mean_percent",Double.class),r.getObject("soil_moisture_mean_m3m3",Double.class))).optional();
    }
    @Deprecated
    public boolean save(Long farmerId,CropType type,java.time.OffsetDateTime plantedAt,DailyWeather weather){
        return saveLegacy(farmerId,type,plantedAt,weather,"MANUAL");
    }
    @Deprecated
    public boolean saveFromApi(Long farmerId,CropType type,java.time.OffsetDateTime plantedAt,DailyWeather weather){
        return saveLegacy(farmerId,type,plantedAt,weather,"OPEN_METEO");
    }
    @Deprecated
    public Optional<DailyWeather> findFromApi(Long farmerId,CropType type,java.time.OffsetDateTime plantedAt){
        String source=jdbc.sql("""
            SELECT weather_source FROM farm.crop_entry
            WHERE farmer_id=:owner AND crop_type=:type AND planted_at=:planted
            """).param("owner",farmerId).param("type",type.name()).param("planted",plantedAt)
            .query(String.class).optional().orElse("");
        return source.equals("OPEN_METEO")?find(farmerId,type,plantedAt):Optional.empty();
    }
    private boolean saveLegacy(Long farmerId,CropType type,java.time.OffsetDateTime plantedAt,
                               DailyWeather w,String source){
        return jdbc.sql("""
            UPDATE farm.crop_entry SET weather_date=:date,weather_timezone=:zone,
                temperature_mean_c=:temp,sunshine_duration_seconds=:sun,
                humidity_mean_percent=:humidity,soil_moisture_mean_m3m3=:soil,weather_source=:source
            WHERE farmer_id=:owner AND crop_type=:type AND planted_at=:planted
              AND (weather_date IS NULL OR weather_date<=:date
                   OR (:source='OPEN_METEO' AND weather_source='MANUAL'))
            """).param("date",w.date()).param("zone",w.timezone())
            .param("temp",w.temperatureMeanC(),Types.DOUBLE).param("sun",w.sunshineDurationSeconds(),Types.DOUBLE)
            .param("humidity",w.humidityMeanPercent(),Types.DOUBLE).param("soil",w.soilMoistureMeanM3M3(),Types.DOUBLE)
            .param("source",source).param("owner",farmerId).param("type",type.name()).param("planted",plantedAt)
            .update()==1;
    }

    private boolean save(Long cycleId,DailyWeather w,String source){
        return jdbc.sql("""
            INSERT INTO public."CROP_WEATHER_READINGS"
                ("Crop_Cycle_ID","Weather_Date","Weather_Timezone","Temperature_Mean_C",
                 "Sunshine_Duration_Seconds","Humidity_Mean_Percent","Soil_Moisture_Mean_M3M3","Weather_Source")
            VALUES (:id,:date,:zone,:temp,:sun,:humidity,:soil,:source)
            ON CONFLICT ("Crop_Cycle_ID") DO UPDATE SET
                "Weather_Date"=EXCLUDED."Weather_Date","Weather_Timezone"=EXCLUDED."Weather_Timezone",
                "Temperature_Mean_C"=EXCLUDED."Temperature_Mean_C",
                "Sunshine_Duration_Seconds"=EXCLUDED."Sunshine_Duration_Seconds",
                "Humidity_Mean_Percent"=EXCLUDED."Humidity_Mean_Percent",
                "Soil_Moisture_Mean_M3M3"=EXCLUDED."Soil_Moisture_Mean_M3M3",
                "Weather_Source"=EXCLUDED."Weather_Source"
            WHERE public."CROP_WEATHER_READINGS"."Weather_Date"<=EXCLUDED."Weather_Date"
               OR (EXCLUDED."Weather_Source"='OPEN_METEO'
                   AND public."CROP_WEATHER_READINGS"."Weather_Source"='MANUAL')
            """).param("id",cycleId).param("date",w.date()).param("zone",w.timezone())
            .param("temp",w.temperatureMeanC(),Types.DOUBLE).param("sun",w.sunshineDurationSeconds(),Types.DOUBLE)
            .param("humidity",w.humidityMeanPercent(),Types.DOUBLE).param("soil",w.soilMoistureMeanM3M3(),Types.DOUBLE)
            .param("source",source).update()==1;
    }
}
