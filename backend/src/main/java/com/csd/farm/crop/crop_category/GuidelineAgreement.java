package com.csd.farm.crop.crop_category;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far the AI's actions agree with the hand-written crop guidelines.
 * This is rule agreement, not a calibrated probability that the advice is correct
 * (docs/ml/03-confidence-plan.md sections 3 and 8).
 */
public record GuidelineAgreement(double score, Level level, List<RecommendedAction> matched,
        List<RecommendedAction> missing, List<RecommendedAction> unexpected) {

    /** Level thresholds: the minimum score for each level, checked in declaration order. */
    public enum Level {
        HIGH(0.75), MEDIUM(0.4), LOW(0);

        final double minimum;
        Level(double minimum) { this.minimum = minimum; }

        static Level of(double score) {
            for (Level level : values()) if (score >= level.minimum) return level;
            return LOW;
        }
    }

    /** Each contraindicated action the AI prescribes subtracts this fraction of the total expected weight. */
    static final double CONTRAINDICATED_PENALTY = 0.5;

    /** Used when nothing is out of range: the right advice is to keep monitoring. */
    public static final Guideline IN_RANGE = new Guideline(Map.of(RecommendedAction.MONITOR, 1.0),
            EnumSet.complementOf(EnumSet.of(RecommendedAction.MONITOR)));

    public static GuidelineAgreement of(List<Guideline> guidelines, Collection<RecommendedAction> aiActions) {
        Map<RecommendedAction, Double> expected = expected(guidelines);
        Set<RecommendedAction> given = given(aiActions);
        List<RecommendedAction> matched = expected.keySet().stream().filter(given::contains).toList();
        List<RecommendedAction> missing = expected.keySet().stream().filter(a -> !given.contains(a)).toList();
        List<RecommendedAction> unexpected = given.stream().filter(a -> !expected.containsKey(a)).toList();
        double score = score(guidelines, aiActions);
        return new GuidelineAgreement(score, Level.of(score), matched, missing, unexpected);
    }

    /** clamp((matched expected weight - contraindicated weight) / total expected weight, 0, 1). */
    public static double score(List<Guideline> guidelines, Collection<RecommendedAction> aiActions) {
        Map<RecommendedAction, Double> expected = expected(guidelines);
        Set<RecommendedAction> given = given(aiActions);
        double total = expected.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) return 0;
        double matched = expected.entrySet().stream()
                .filter(e -> given.contains(e.getKey())).mapToDouble(Map.Entry::getValue).sum();
        long contraindicated = guidelines.stream().flatMap(g -> g.contraindicated().stream())
                .distinct().filter(a -> given.contains(a) && !expected.containsKey(a)).count();
        double score = (matched - contraindicated * CONTRAINDICATED_PENALTY * total) / total;
        return Math.max(0, Math.min(1, score));
    }

    // Weights add up when both temperature and sunshine expect the same action.
    private static Map<RecommendedAction, Double> expected(List<Guideline> guidelines) {
        Map<RecommendedAction, Double> expected = new EnumMap<>(RecommendedAction.class);
        guidelines.forEach(g -> g.expected().forEach((a, w) -> expected.merge(a, w, Double::sum)));
        return expected;
    }

    // An AI that prescribes nothing is advising the farmer to keep monitoring.
    private static Set<RecommendedAction> given(Collection<RecommendedAction> aiActions) {
        return aiActions == null || aiActions.isEmpty() ? EnumSet.of(RecommendedAction.MONITOR) : EnumSet.copyOf(aiActions);
    }
}
