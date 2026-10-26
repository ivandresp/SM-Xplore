package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

public record RecommendationSignalsResponse(
        double affinity,
        double quality,
        Double distanceKm,
        double novelty
) {
}
