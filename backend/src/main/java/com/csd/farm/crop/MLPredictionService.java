package com.csd.farm.crop;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import com.csd.farm.crop.CropResponses.Prediction;

@Service
public class MLPredictionService {
    private final JdbcClient jdbc;
    private final MLPredictionClient model;

    public MLPredictionService(JdbcClient jdbc, MLPredictionClient model) {
        this.jdbc = jdbc;
        this.model = model;
    }

    Prediction synthetic(CropType crop, int age, String scenario){
        return model.predict(Map.of("mode", "synthetic", "crop", crop.name(), "age", age, "scenario", scenario));
    }


    // CropService verifies the logged-in farmer owns the cycle before this read.
    Prediction forPlanting(CropEntry entry) {
        try {
            // The environment producer uses the server timezone for this date range.
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            var row = jdbc.sql("""
                    SELECT "Start_Date", "End_Date", "Temperature_Mean", "Soil_Moisture_Mean",
                           "Vapour_Pressure_Deficit_Mean", "Sunshine_Duration_Mean_Seconds"
                    FROM public."CROP_WEATHER_HISTORICAL_AVERAGE"
                    WHERE "Crop_Cycle_ID" = :cycle
                    """).param("cycle", entry.cropCycleId()).query((r, n) -> new Average(
                        r.getObject("Start_Date", LocalDate.class), r.getObject("End_Date", LocalDate.class),
                        r.getObject("Temperature_Mean", Double.class), r.getObject("Soil_Moisture_Mean", Double.class),
                        r.getObject("Vapour_Pressure_Deficit_Mean", Double.class),
                        r.getObject("Sunshine_Duration_Mean_Seconds", Double.class))).optional();
            if (row.isEmpty()) return reference(entry, "No saved 14-day weather averages were found for this planting.");
            Average average = row.get();
            if (!today.minusDays(14).equals(average.start()) || !today.minusDays(1).equals(average.end())
                    || average.temperature() == null || average.soil() == null || average.vpd() == null
                    || average.sunshine() == null) return reference(entry,
                            "Saved weather averages are incomplete or do not cover the previous 14 complete days.");
            var request = new java.util.HashMap<String, Object>();
            request.put("mode", "averages");
            request.put("crop", entry.cropType().name());
            request.put("prediction_date", today.toString());
            request.put("sowing_date", entry.plantedAt().atZoneSameInstant(zone).toLocalDate().toString());
            request.put("start_date", average.start().toString());
            request.put("end_date", average.end().toString());
            request.put("temperature", average.temperature());
            request.put("soil", average.soil());
            request.put("vpd", average.vpd());
            request.put("sunshine_seconds", average.sunshine());
            Prediction prediction = model.predict(request);
            return "SYNTHETIC_DEMO".equals(prediction.status()) ? prediction
                    : reference(entry, prediction.message());
        } catch (DataAccessException | DateTimeException failure) {
            return reference(entry, "Stored 14-day weather averages could not be read for this planting.");
        }
    }

    private Prediction reference(CropEntry entry, String reason) {
        ZoneId zone = ZoneId.systemDefault();
        Prediction target = model.predict(Map.of("mode", "reference", "crop", entry.cropType().name(),
                "prediction_date", LocalDate.now(zone).toString(),
                "sowing_date", entry.plantedAt().atZoneSameInstant(zone).toLocalDate().toString()));
        return new Prediction(target.status(), null, target.unit(), reason,
                null, target.referenceEstimate(), target.projectionDate());
    }

    private record Average(LocalDate start, LocalDate end, Double temperature, Double soil,
                           Double vpd, Double sunshine) {}
}
