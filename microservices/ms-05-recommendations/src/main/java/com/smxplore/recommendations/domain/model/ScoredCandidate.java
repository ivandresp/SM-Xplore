package com.smxplore.recommendations.domain.model;

public record ScoredCandidate(
        RecommendationCandidate candidate,
        double score,
        RecommendationSignals signals
) {
}
