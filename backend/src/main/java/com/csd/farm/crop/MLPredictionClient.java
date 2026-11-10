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
            from predict import predict
            request = json.load(sys.stdin)
            if request.get('mode') == 'averages':
                from average_prediction import compare
                result = compare(request)
            elif request.get('mode') == 'synthetic':
                from synthetic_demo import simulate
                result = simulate(request)
            else:
                result = predict(request)
            print(json.dumps(result, allow_nan=False))
            """;

    CropResponses.Prediction sampleWheat() {
        try {
            var sample = json.readValue(Files.readString(directory.resolve("data/example_request.json")),
                    new tools.jackson.core.type.TypeReference<Map<String,Object>>() {});
            return predict(sample);
        } catch (IOException | RuntimeException failure) {
            return CropResponses.Prediction.unavailable();
        }
    }

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
                if ("synthetic".equals(input.get("mode")) || "averages".equals(input.get("mode"))) {
                    if ("SYNTHETIC_DEMO".equals(status) && result.path("value").isNumber())
                        return new CropResponses.Prediction(status, result.path("value").asDouble(),
                                result.path("unit").asString(), result.path("message").asString());
                    if ("UNSUPPORTED".equals(status))
                        return new CropResponses.Prediction(status, null, null, result.path("message").asString());
                    return CropResponses.Prediction.unavailable();
                }
                if ("UNSUPPORTED".equals(status))
                    return new CropResponses.Prediction(status, null, null, "This planting is outside the model's training region or growing conditions.");
                if ("INVALID_INPUT".equals(status))
                    return new CropResponses.Prediction(status, null, null, "Check the planting date and daily weather measurements.");
                var value = result.path("value");
                if ("EXPERIMENTAL".equals(status) && value.isNumber() && Double.isFinite(value.asDouble())
                        && value.asDouble() >= 0 && "g/m2".equals(result.path("unit").asString(""))) {
                    return new CropResponses.Prediction(status, value.asDouble(), "g/m2",
                            String.format(java.util.Locale.ROOT, "Experimental dry aboveground biomass estimate: %.1f g/m².", value.asDouble()));
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
