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
    private static final String PROFILE = "ifs-daily-soil-0-7-v2";
    private final JdbcClient jdbc;
    private final MLPredictionClient model;

    public MLPredictionService(JdbcClient jdbc, MLPredictionClient model) {
        this.jdbc = jdbc;
        this.model = model;
    }

    Prediction synthetic(CropType crop, int age, String scenario){
        return model.predict(Map.of("mode", "synthetic", "crop", crop.name(), "age", age, "scenario", scenario));
    }

    Prediction sampleWheat(){return model.sampleWheat();}

    // Called only after CropService has resolved this cycle for its authenticated owner.
    Prediction forPlanting(CropEntry entry) {
        if (entry.cropType() != CropType.WHEAT)
            return new Prediction("UNSUPPORTED", null, null, "Biomass estimates are available for outdoor winter wheat only.");
        try {
            var rows = jdbc.sql("""
                    SELECT date, timezone, temperature_min_c, temperature_max_c,
                           soil_moisture_m3_m3, solar_radiation_mj_m2
                    FROM public.ml_daily_weather
                    WHERE crop_cycle_id = :cycle AND profile = :profile
                      AND crop = 'winter_wheat' AND growing_system = 'outdoor_soil'
                      AND date < (CURRENT_TIMESTAMP AT TIME ZONE timezone)::date
                    ORDER BY date DESC LIMIT 15
                    """).param("cycle", entry.cropCycleId()).param("profile", PROFILE)
                    .query((r, n) -> new Day(r.getObject("date", LocalDate.class), r.getString("timezone"),
                            r.getObject("temperature_min_c", Double.class), r.getObject("temperature_max_c", Double.class),
                            r.getObject("soil_moisture_m3_m3", Double.class), r.getObject("solar_radiation_mj_m2", Double.class))).list();
            if (rows.size() < 14) return pending();
            ZoneId zone = ZoneId.of(rows.get(0).timezone());
            LocalDate today = LocalDate.now(zone);
            List<Day> window = new java.util.ArrayList<>(14);
            for (int i = 13; i >= 0; i--) {
                Day day = rows.get(i);
                if (!day.date().equals(today.minusDays(i + 1)) || !day.timezone().equals(zone.getId())
                        || day.temperature_min_c() == null || day.temperature_max_c() == null
                        || day.soil_moisture_m3_m3() == null || day.solar_radiation_mj_m2() == null) return pending();
                window.add(day);
            }
            if (rows.size() == 15 && rows.get(14).date().equals(window.get(0).date())) return pending();
            return model.predict(Map.of("profile", PROFILE, "crop", "winter_wheat", "growing_system", "outdoor_soil",
                    "latitude", entry.latitude(), "longitude", entry.longitude(), "timezone", zone.getId(),
                    "sowing_date", entry.plantedAt().atZoneSameInstant(zone).toLocalDate().toString(),
                    "prediction_date", today.toString(), "weather", window));
        } catch (DataAccessException | DateTimeException failure) {
            return new Prediction("UNAVAILABLE", null, null, "Compatible weather history is unavailable for this planting.");
        }
    }

    private static Prediction pending() {
        return new Prediction("INSUFFICIENT_DATA", null, null,
                "A biomass estimate needs confirmed outdoor winter wheat and 14 complete days of matching weather.");
    }

    private record Day(LocalDate date, String timezone, Double temperature_min_c, Double temperature_max_c,
                       Double soil_moisture_m3_m3, Double solar_radiation_mj_m2) {}
}
