package com.csd.farm.environment;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mimics the JSON response of OpenMeteo
 * 
 *  This class will auto-convert the values returned by OpenMeteo to the correct data type
 * 
 *  Only used by WeatherService to organise these readings into an EnvironmentReading object
 */
public class OpenMeteoResponse {
    public HourlyBlock hourly;
    public DailyBlock daily;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HourlyBlock {
        public List<String> time;
        public List<Double> temperature_2m;
        public List<Double> soil_moisture_3_to_9cm;
        public List<Double> vapour_pressure_deficit;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyBlock {
        public List<String> time;
        public List<Double> sunshine_duration;
    }
}

