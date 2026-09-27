package com.csd.farm.environment;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Structure of how each reading must be presented
 * 
 *  Provides an easy way for the team to process the readings
 * 
 *  Will have to change this if the team decides include more types of readings
 */
public record EnvironmentReading(
    LocalDateTime dateTime,
    Double temperature,
    Double soilMoisture,
    LocalDate date,
    Double sunshineDuration
) {}