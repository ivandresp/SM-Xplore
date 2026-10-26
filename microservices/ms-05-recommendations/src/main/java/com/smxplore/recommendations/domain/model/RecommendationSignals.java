package com.smxplore.recommendations.domain.model;

public record RecommendationSignals(
        double affinity,
        double quality,
        Double distanceKm,
        double novelty
) {
}
