package com.csd.farm.crop;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import com.csd.farm.crop.BiomassFormula.Weather;
import com.csd.farm.crop.CropResponses.Prediction;

@Service
public class BiomassEstimateService {
    private final JdbcClient jdbc;
    private final BiomassFormula formula;
    private final YieldModelClient yieldModel;

    public BiomassEstimateService(JdbcClient jdbc, BiomassFormula formula, YieldModelClient yieldModel) {
        this.jdbc = jdbc;
        this.formula = formula;
        this.yieldModel = yieldModel;
    }

    // CropService verifies the logged-in farmer owns the cycle before this read.
    Prediction forPlanting(CropEntry entry) {
        // The environment producer uses the server timezone for this date range.
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        try {
            LocalDate planted = entry.plantedAt().atZoneSameInstant(zone).toLocalDate();
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
            if (row.isEmpty()) return formula.reference(entry.cropType(), planted, today,
                    "No saved 14-day weather averages were found for this planting.");
            Average average = row.get();
            if (!today.minusDays(14).equals(average.start()) || !today.minusDays(1).equals(average.end())
                    || average.temperature() == null || average.soil() == null || average.vpd() == null
                    || average.sunshine() == null) return formula.reference(entry.cropType(), planted, today,
                            "Saved weather averages are incomplete or do not cover the previous 14 complete days.");
            Prediction estimate = formula.estimate(entry.cropType(), planted, today, new Weather(
                    average.temperature(), average.soil(), average.vpd(), average.sunshine() / 3600));
            if (BiomassFormula.INVALID_INPUT.equals(estimate.status()))
                return formula.reference(entry.cropType(), planted, today, estimate.message());
            // The ML yield outlook uses the same four averages plus the farm's location.
            return estimate.withYield(yieldModel.predict(Map.of(
                    "crop", entry.cropType().name(), "latitude", entry.latitude(),
                    "longitude", entry.longitude(), "temperature", average.temperature(),
                    "soil", average.soil(), "vpd", average.vpd(), "sunshine_seconds", average.sunshine())));
        } catch (DataAccessException failure) {
            return formula.reference(entry.cropType(),
                    entry.plantedAt().atZoneSameInstant(zone).toLocalDate(), today,
                    "Stored 14-day weather averages could not be read for this planting.");
        } catch (DateTimeException failure) {
            return new Prediction(BiomassFormula.INVALID_INPUT, null, null,
                    "The planting date could not be read.");
        }
    }

    private record Average(LocalDate start, LocalDate end, Double temperature, Double soil,
                           Double vpd, Double sunshine) {}
}
