package com.csd.farm.environment;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mimics the JSON response of OpenMeteo
 * 
 *  This class will auto-convert the values returned by OpenMeteo to the correct data types
 * 
 *  Only used by EnvironmentService to organise its readings into an EnvironmentReading object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponse {
    public HourlyBlock hourly;
    public DailyBlock daily;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HourlyBlock {
        public List<String> time;

        @JsonAlias("temperature_2m")
        public List<Double> temperature;

        @JsonAlias({"soil_moisture_3_to_9cm", "soil_moisture_0_to_7cm"})
        public List<Double> soil_moisture;
        
        public List<Double> vapour_pressure_deficit;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyBlock {
        public List<String> time;
        public List<Double> sunshine_duration;
    }
}

