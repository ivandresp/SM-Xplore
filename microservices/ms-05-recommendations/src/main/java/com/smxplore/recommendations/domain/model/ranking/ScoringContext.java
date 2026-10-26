package com.smxplore.recommendations.domain.model.ranking;

import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;

public record ScoringContext(
        GeoLocation location,
        double maxAffinity,
        boolean hasAffinitySignals
) {

    public ScoringContext(GeoLocation location, double maxAffinity, boolean hasAffinitySignals) {
        this.location = location;
        this.maxAffinity = maxAffinity;
        this.hasAffinitySignals = hasAffinitySignals;
        validate();
    }

    public void validate() {
        validateMaxAffinity();
    }

    private void validateMaxAffinity() {
        if (maxAffinity < 0.0) {
            throw new InvalidScoringException("maxAffinity must be >= 0");
        }
    }
}
