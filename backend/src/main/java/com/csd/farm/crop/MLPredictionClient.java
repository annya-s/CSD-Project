package com.csd.farm.crop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MLPredictionClient {
    private final ObjectMapper json;
    private final Path directory;
    private final String python;
    private final Semaphore slots = new Semaphore(2);

    public MLPredictionClient(ObjectMapper json, @Value("${ml.directory:}") String directory,
            @Value("${ml.python:}") String python) {
        this.json = json;
        Path defaultPath = Files.isDirectory(Path.of("ml")) ? Path.of("ml") : Path.of("../ml");
        this.directory = (directory.isBlank() ? defaultPath : Path.of(directory)).toAbsolutePath().normalize();
        this.python = python.isBlank() ? defaultPython(this.directory) : python;
    }

    private static String defaultPython(Path directory) {
        for (String executable : new String[]{".venv/bin/python", ".venv/Scripts/python.exe"}) {
            Path candidate = directory.resolve(executable);
            if (Files.isRegularFile(candidate)) return candidate.toString();
        }
        return "python3";
    }

    // stdin avoids temporary files and works with Windows Python launched from WSL.
    private static final String RUN = """
            import json, sys
            request = json.load(sys.stdin)
            if request.get('mode') in ('averages', 'reference'):
                from average_prediction import compare
                result = compare(request)
            elif request.get('mode') == 'synthetic':
                from synthetic_demo import simulate
                result = simulate(request)
            else:
                result = dict(status='INVALID_INPUT', message='Unknown prediction mode.')
            print(json.dumps(result, allow_nan=False))
            """;

    CropResponses.Prediction predict(Map<String, Object> input) {
        if (!slots.tryAcquire()) return CropResponses.Prediction.unavailable();
        Process process = null;
        try {
            process = new ProcessBuilder(python, "-c", RUN).directory(directory.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(json.writeValueAsBytes(input));
            }
            if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0)
                return CropResponses.Prediction.unavailable();
            try (var stdout = process.getInputStream()) {
                var result = json.readTree(stdout);
                String status = result.path("status").asString("");
                if ("REFERENCE_ONLY".equals(status) && result.path("reference_conditions").isNumber())
                    return new CropResponses.Prediction(status, null,
                            result.path("unit").asString(), result.path("message").asString(),
                            null, result.path("reference_conditions").asDouble(),
                            result.path("projection_date").asString(null));
                if ("synthetic".equals(input.get("mode")) || "averages".equals(input.get("mode"))
                        || "reference".equals(input.get("mode"))) {
                    if ("SYNTHETIC_DEMO".equals(status) && result.path("value").isNumber())
                        return new CropResponses.Prediction(status, result.path("value").asDouble(),
                                result.path("unit").asString(), result.path("message").asString(),
                                result.path("current_estimate").isNumber() ? result.path("current_estimate").asDouble() : null,
                                result.path("reference_conditions").isNumber() ? result.path("reference_conditions").asDouble() : null,
                                result.path("projection_date").asString(null));
                    if ("UNSUPPORTED".equals(status) || "INVALID_INPUT".equals(status) || "UNAVAILABLE".equals(status))
                        return new CropResponses.Prediction(status, null, null, result.path("message").asString());
                    return CropResponses.Prediction.unavailable();
                }

            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            // The rest of the crop details remain available when the model cannot run.
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            slots.release();
        }
        return CropResponses.Prediction.unavailable();
    }
}
