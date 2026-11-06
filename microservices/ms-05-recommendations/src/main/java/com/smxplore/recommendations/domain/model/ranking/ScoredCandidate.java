package com.smxplore.recommendations.domain.model.ranking;

import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;

public record ScoredCandidate(
        RecommendationCandidate candidate,
        double score,
        RecommendationSignals signals
) {

    public ScoredCandidate(RecommendationCandidate candidate, double score, RecommendationSignals signals) {
        this.candidate = candidate;
        this.score = score;
        this.signals = signals;
        validate();
    }

    public void validate() {
        validateCandidate();
        validateScore();
        validateSignals();
    }

    private void validateCandidate() {
        if (candidate == null) {
            throw new InvalidScoringException("candidate is required");
        }
    }

    private void validateScore() {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new InvalidScoringException("score must be between 0 and 1");
        }
    }

    private void validateSignals() {
        if (signals == null) {
            throw new InvalidScoringException("signals are required");
        }
    }
}
