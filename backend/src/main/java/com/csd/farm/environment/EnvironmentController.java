package com.csd.farm.environment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** In charge of handling OUR user's requests regarding environment
 * 
 *  i.e Getting them their readings
 */
@RestController
public class EnvironmentController {
    private final EnvironmentService envService;

    public EnvironmentController(EnvironmentService envService) {
        this.envService = envService;
    }

    @GetMapping("/api/environment/latest")
    public EnvironmentReading getLatestEnvironnmentReading(@RequestParam double latitude, @RequestParam double longitude) {
        return envService.getLatestReading(latitude, longitude);
    }
}

