package com.csd.farm.crop.crop_category;

import java.util.Map;
import java.util.Set;

/**
 * Hand-written guidance for one (variable, status) pair: the actions we expect, with weights,
 * and actions that would be wrong in that situation.
 */
public record Guideline(Map<RecommendedAction, Double> expected, Set<RecommendedAction> contraindicated) {
}
