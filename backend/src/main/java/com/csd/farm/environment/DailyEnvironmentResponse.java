package com.csd.farm.environment;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Unix timestamps keep local-day aggregation correct across timezones and clock changes.
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyEnvironmentResponse {
    public String timezone;
    public Map<String, String> daily_units;
    public Map<String, String> hourly_units;
    public Daily daily;
    public Hourly hourly;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Daily {
        public List<Long> time;
        public List<Double> temperature_2m_mean;
        public List<Double> sunshine_duration;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hourly {
        public List<Long> time;
        public List<Double> relative_humidity_2m;
        public List<Double> soil_moisture_3_to_9cm;
    }
}
