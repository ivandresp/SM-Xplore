package com.smxplore.recommendations.domain.model;

public record ScoringContext(
        GeoLocation location,
        double maxAffinity,
        boolean hasAffinitySignals
) {
}
