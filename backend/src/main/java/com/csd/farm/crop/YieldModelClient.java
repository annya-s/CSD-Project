package com.csd.farm.crop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.csd.farm.crop.CropResponses.YieldOutlook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runs the trained yield model (ml/yield_predict.py) in a short-lived Python process. */
@Component
public class YieldModelClient {
    private final ObjectMapper json;
    private final Path directory;
    private final String python;
    private final Semaphore slots = new Semaphore(2);

    public YieldModelClient(ObjectMapper json, @Value("${ml.directory:}") String directory,
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
            from yield_predict import predict
            print(json.dumps(predict(json.load(sys.stdin)), allow_nan=False))
            """;

    YieldOutlook predict(Map<String, Object> input) {
        if (!slots.tryAcquire()) return YieldOutlook.unavailable();
        Process process = null;
        try {
            process = new ProcessBuilder(python, "-c", RUN).directory(directory.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(json.writeValueAsBytes(input));
            }
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0)
                return YieldOutlook.unavailable();
            try (var stdout = process.getInputStream()) {
                JsonNode result = json.readTree(stdout);
                String status = result.path("status").asString("");
                if ("ML_ESTIMATE".equals(status) && result.path("expected_yield").isNumber())
                    return new YieldOutlook(status, result.path("expected_yield").asDouble(),
                            result.path("normal_yield").asDouble(), result.path("ratio").asDouble(),
                            result.path("unit").asString(), result.path("region").asString(),
                            result.path("model").asString(), result.path("message").asString());
                if (!status.isBlank())
                    return new YieldOutlook(status, null, null, null, null, null, null,
                            result.path("message").asString());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            // The rest of the crop details remain available when the model cannot run.
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            slots.release();
        }
        return YieldOutlook.unavailable();
    }
}
