package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.csd.farm.crop.CropRepository;

/** In charge of packaging the returned data from the (multiple) external API calls into EnvironmentReading(s)
 * 
 *  Sends the result back to EnvironmentController
 */
@Service
public class EnvironmentService {

    // TODO: add the CropRepo or whatever to get latitude/longitude from DB
    private final EnvironmentClient envClient;
    private final CropRepository cropRepo;

    public EnvironmentService(EnvironmentClient envClient, CropRepository cropRepo) {
        this.envClient = envClient;
        this.cropRepo = cropRepo;
    }

    public List<EnvironmentReading> getAllReadings(int cropId) throws RuntimeException {
        CropRepository.FarmCoordinates coords = cropRepo.findCoordinatesForFarm(cropId)
            .orElseThrow(() -> new NoSuchElementException("Crop coordinates not found"));

        double latitude = coords.latitude();
        double longitude = coords.longitude();

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude/latitude inputs");
        }

        List<EnvironmentReading> readings = new ArrayList<>();

        OpenMeteoResponse res = envClient.fetchLatestMeteoData(latitude, longitude);

        // TODO: Make error checking cleaner
        if (res == null) {
            throw new NoSuchElementException("No response returned from Open-Meteo");
        }

        if (res.hourly == null || res.hourly.time == null || res.hourly.time.isEmpty()) {
            throw new NoSuchElementException("No hourly data returned from Open-Meteo");
        }

        if (res.daily == null || res.daily.time == null || res.daily.time.isEmpty()) {
            throw new NoSuchElementException("No daily data returned from Open-Meteo");
        }
        
        for (int i = 0; i < res.hourly.time.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(res.hourly.time.get(i));
            Double temperature = getValue(res.hourly.temperature, i);
            Double soilMoisture = getValue(res.hourly.soil_moisture, i);
            Double vapourPressureDeficit = getValue(res.hourly.vapour_pressure_deficit, i);

            LocalDate date = dateTime.toLocalDate();
            int dateIndex = res.daily.time.indexOf(date.toString());
            Double sunshineDuration = getValue(res.daily.sunshine_duration, dateIndex);

            readings.add(new EnvironmentReading(
                dateTime, 
                temperature, 
                soilMoisture,
                vapourPressureDeficit,
                date, 
                sunshineDuration)
            );
        }
        return readings;
    }

    // TODO: Change the parameter to ONLY "int cropId"
    public EnvironmentReading getLatestReading(int cropId) {
        int currentHour = LocalTime.now().getHour();

        // TODO: Change these arguments to cropId
        return getAllReadings(cropId).get(currentHour);
    }

    public List<EnvironmentReading> getHistoricalReading(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude/latitude inputs");
        }

        List<EnvironmentReading> readings = new ArrayList<>();

        OpenMeteoResponse res = envClient.fetchHistoricalMeteoData(latitude, longitude);

        // TODO: Make error checking cleaner
        if (res == null) {
            throw new NoSuchElementException("No response returned from Open-Meteo");
        }

        if (res.hourly == null || res.hourly.time == null || res.hourly.time.isEmpty()) {
            throw new NoSuchElementException("No hourly data returned from Open-Meteo");
        }

        if (res.daily == null || res.daily.time == null || res.daily.time.isEmpty()) {
            throw new NoSuchElementException("No daily data returned from Open-Meteo");
        }

        for (int i = 0; i < res.hourly.time.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(res.hourly.time.get(i));
            LocalDate date = dateTime.toLocalDate();

            Double temperature = getValue(res.hourly.temperature, i);
            Double soilMoisture = getValue(res.hourly.soil_moisture, i);
            Double vapourPressureDeficit = getValue(res.hourly.vapour_pressure_deficit, i);

            int dateIndex = res.daily.time.indexOf(date.toString());
            Double sunshineDuration = getValue(res.daily.sunshine_duration, dateIndex);

            readings.add(new EnvironmentReading(
                dateTime,
                temperature,
                soilMoisture,
                vapourPressureDeficit,
                date,
                sunshineDuration
            ));
        }

        return readings;
    }

    private Double getValue(List<Double> values, int index) {
        return values != null && index >= 0 && index < values.size() ?
            values.get(index) : null;
    }
}



