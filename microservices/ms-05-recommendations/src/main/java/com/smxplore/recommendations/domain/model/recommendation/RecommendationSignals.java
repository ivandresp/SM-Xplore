package com.smxplore.recommendations.domain.model.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import lombok.Builder;

@Builder
public record RecommendationSignals(
        double affinity,
        double quality,
        Double distanceKm,
        double novelty
) {

    public void validate() {
        validateAffinity();
        validateQuality();
        validateDistance();
        validateNovelty();
    }

    private void validateAffinity() {
        if (!Double.isFinite(affinity) || affinity < 0.0 || affinity > 1.0) {
            throw new InvalidScoringException("affinity must be between 0 and 1");
        }
    }

    private void validateQuality() {
        if (!Double.isFinite(quality) || quality < 0.0 || quality > 1.0) {
            throw new InvalidScoringException("quality must be between 0 and 1");
        }
    }

    private void validateDistance() {
        if (distanceKm != null && (!Double.isFinite(distanceKm) || distanceKm < 0.0)) {
            throw new InvalidScoringException("distanceKm must be >= 0 when present");
        }
    }

    private void validateNovelty() {
        if (!Double.isFinite(novelty) || novelty < 0.0 || novelty > 1.0) {
            throw new InvalidScoringException("novelty must be between 0 and 1");
        }
    }
}
