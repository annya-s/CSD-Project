package com.csd.farm.crop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.csd.farm.crop.CropResponses.Cause;
import com.csd.farm.crop.CropResponses.Prediction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-growth biomass estimate from a documented, assumption-based formula.
 *
 * <p>For each crop, {@code crop-profiles.json} gives a growth curve, a full-growth
 * target and optimal values for four 14-day weather averages. Each average that
 * differs from its optimum removes a share of the target:
 *
 * <pre>factor = clip(1 - 0.025*|T-Tref| - 2*|M-Mref| - 0.12*|V-Vref| - 0.025*|S-Sref|, 0, 1)</pre>
 *
 * <p>Because the penalties are added together, the total shortfall can be split
 * exactly by cause. The coefficients and most optimal values are assumptions,
 * not field-calibrated responses.
 */
@Component
public class BiomassFormula {
    static final String ESTIMATE = "ESTIMATE";
    static final String REFERENCE_ONLY = "REFERENCE_ONLY";
    static final String UNSUPPORTED = "UNSUPPORTED";
    static final String INVALID_INPUT = "INVALID_INPUT";

    /** One weather input of the formula, in the order used by crop-profiles.json "reference". */
    private record Factor(String label, String unit, double coefficient, double min, double max) {}

    private static final List<Factor> FACTORS = List.of(
            new Factor("Temperature", "°C", 0.025, -50, 50),
            new Factor("Soil moisture", "m³/m³", 2.0, 0, 0.6),
            new Factor("Vapour pressure deficit", "kPa", 0.12, 0, 5),
            new Factor("Sunshine", "h/day", 0.025, 0, 24));

    record Profile(double[] ages, double[] biomass, String unit, double[] reference,
                   int maturityDays, double matureTarget) {}

    /** The four 14-day averages; sunshine is in hours per day. */
    record Weather(double temperature, double soilMoisture, double vpd, double sunshineHours) {
        double[] values() { return new double[]{temperature, soilMoisture, vpd, sunshineHours}; }
    }

    private final Map<String, Profile> profiles;

    public BiomassFormula() {
        this.profiles = load();
    }

    /** Full-growth target only, used when no usable weather averages exist. */
    Prediction reference(CropType crop, LocalDate planted, LocalDate today, String reason) {
        Profile profile = profiles.get(crop.name());
        if (profile == null) return unsupported();
        long age = ChronoUnit.DAYS.between(planted, today);
        if (age < 0 || age > profile.maturityDays()) return pastMaturity(age, profile);
        return new Prediction(REFERENCE_ONLY, null, profile.unit(), reason, null,
                profile.matureTarget(), planted.plusDays(profile.maturityDays()).toString(), List.of(), null);
    }

    /** Estimate at full growth if the recent 14-day averages continue. */
    Prediction estimate(CropType crop, LocalDate planted, LocalDate today, Weather weather) {
        Profile profile = profiles.get(crop.name());
        if (profile == null) return unsupported();
        long age = ChronoUnit.DAYS.between(planted, today);
        if (age < 0 || age > profile.maturityDays()) return pastMaturity(age, profile);

        double[] actual = weather.values();
        double[] penalties = new double[FACTORS.size()];
        double total = 0;
        for (int i = 0; i < FACTORS.size(); i++) {
            Factor factor = FACTORS.get(i);
            if (!Double.isFinite(actual[i]) || actual[i] < factor.min() || actual[i] > factor.max())
                return new Prediction(INVALID_INPUT, null, null,
                        "Saved weather averages are outside the supported range.");
            penalties[i] = factor.coefficient() * Math.abs(actual[i] - profile.reference()[i]);
            total += penalties[i];
        }
        double growthFactor = Math.max(0, Math.min(1, 1 - total));
        // If the penalties exceed 100%, scale each cause so the shares add up to the real loss.
        double scale = total > 1 ? 1 / total : 1;

        List<Cause> causes = new ArrayList<>();
        for (int i = 0; i < FACTORS.size(); i++) {
            if (penalties[i] <= 0) continue;
            Factor factor = FACTORS.get(i);
            double optimal = profile.reference()[i];
            causes.add(new Cause(factor.label(), actual[i], optimal, factor.unit(),
                    100 * penalties[i] * scale, actual[i] < optimal ? "too low" : "too high"));
        }
        causes.sort(Comparator.comparingDouble(Cause::shortfallPercent).reversed());

        double target = profile.matureTarget();
        double atFullGrowth = target * growthFactor;
        double current = interpolate(age, profile.ages(), profile.biomass()) * growthFactor;
        return new Prediction(ESTIMATE, atFullGrowth, profile.unit(),
                "Formula estimate assuming the last 14 days' average weather continues until full growth. "
                        + "The weather effects are assumptions, not field-validated measurements.",
                current, target, planted.plusDays(profile.maturityDays()).toString(), causes, null);
    }

    private static Prediction unsupported() {
        return new Prediction(UNSUPPORTED, null, null, "No biomass profile is available for this crop.");
    }

    private static Prediction pastMaturity(long age, Profile profile) {
        return new Prediction(UNSUPPORTED, null, profile.unit(), "Plant age is " + age
                + " days. This crop's full-growth estimate supports planting ages 0–"
                + profile.maturityDays() + " days.");
    }

    /** Linear interpolation, clamped at both ends (same as numpy.interp). */
    static double interpolate(double x, double[] xs, double[] ys) {
        if (x <= xs[0]) return ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }

    private static Map<String, Profile> load() {
        try (InputStream input = BiomassFormula.class.getResourceAsStream("/crop-profiles.json")) {
            if (input == null) throw new IllegalStateException("crop-profiles.json is missing");
            JsonNode root = new ObjectMapper().readTree(input);
            Map<String, Profile> result = new HashMap<>();
            for (var entry : root.properties()) {
                JsonNode node = entry.getValue();
                result.put(entry.getKey(), new Profile(numbers(node.path("ages")),
                        numbers(node.path("biomass")), node.path("unit").asString(),
                        numbers(node.path("reference")), node.path("maturity_days").asInt(),
                        node.path("mature_target").asDouble()));
            }
            return Map.copyOf(result);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static double[] numbers(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).asDouble();
        return values;
    }
}
