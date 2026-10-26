package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ranking.ScoredCandidate;
import com.smxplore.recommendations.domain.model.ranking.ScoringContext;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Application-level ranking policy. It receives already-filtered candidates and
 * combines affinity, review quality, proximity and novelty. It never uses
 * reservations.
 */
@RequiredArgsConstructor
public class RecommendationScorer {

    private final RecommendationPolicy policy;

    public ScoredCandidate score(RecommendationCandidate candidate, ScoringContext context, Instant now) {
        validateArguments(candidate, context, now);
        return buildScoredCandidate(candidate, context, now);
    }

    private ScoredCandidate buildScoredCandidate(
            RecommendationCandidate candidate,
            ScoringContext context,
            Instant now
    ) {
        double affinity = calculateAffinity(candidate, context);
        double quality = calculateQuality(candidate);
        Proximity proximity = calculateProximity(candidate, context);
        double novelty = calculateNovelty(candidate, now);
        double score = calculateWeightedScore(affinity, quality, proximity.normalized(), novelty, context);

        RecommendationSignals signals = RecommendationSignals.builder()
                .affinity(affinity)
                .quality(quality)
                .distanceKm(proximity.distanceKm())
                .novelty(novelty)
                .build();
        signals.validate();
        return new ScoredCandidate(candidate, score, signals);
    }

    private double calculateAffinity(RecommendationCandidate candidate, ScoringContext context) {
        if (context.maxAffinity() <= 0.0 || candidate.affinity() <= 0.0) {
            return 0.0;
        }
        return clamp(candidate.affinity() / context.maxAffinity(), 0.0, 1.0);
    }

    private double calculateQuality(RecommendationCandidate candidate) {
        double bayesian = bayesianRating(candidate.ratingAvg(), candidate.ratingCount());
        return clamp((bayesian - 1.0) / 4.0, 0.0, 1.0);
    }

    private Proximity calculateProximity(RecommendationCandidate candidate, ScoringContext context) {
        GeoLocation candidateLocation = candidate.locationOrNull();
        if (context.location() == null || candidateLocation == null) {
            return Proximity.none();
        }
        double distanceKm = candidateLocation.distanceKmTo(context.location());
        double normalized = Math.exp(-distanceKm / policy.proximityRadiusKm());
        return new Proximity(distanceKm, normalized);
    }

    private double calculateNovelty(RecommendationCandidate candidate, Instant now) {
        if (candidate.updatedAt() == null) {
            return 0.5;
        }
        double days = Math.max(0.0, Duration.between(candidate.updatedAt(), now).toMinutes() / 1440.0);
        return Math.exp(-days / policy.noveltyHalfLifeDays());
    }

    private double calculateWeightedScore(
            double affinity,
            double quality,
            double proximity,
            double novelty,
            ScoringContext context
    ) {
        Weights weights = adjustedWeights(context);
        double score = weights.affinity() * affinity
                + weights.quality() * quality
                + weights.proximity() * proximity
                + weights.novelty() * novelty;
        return clamp(score, 0.0, 1.0);
    }

    private Weights adjustedWeights(ScoringContext context) {
        double affinity = context.hasAffinitySignals() ? policy.affinityWeight() : 0.0;
        double quality = policy.qualityWeight();
        double proximity = context.location() != null ? policy.proximityWeight() : 0.0;
        double novelty = policy.noveltyWeight();
        double sum = affinity + quality + proximity + novelty;

        if (sum <= 0.0) {
            return new Weights(0.0, 0.0, 0.0, 0.0);
        }
        return new Weights(affinity / sum, quality / sum, proximity / sum, novelty / sum);
    }

    /**
     * Bayesian average with a configurable prior. This prevents an item with a
     * single 5-star review from outranking an item with many good reviews.
     */
    double bayesianRating(double ratingAvg, long ratingCount) {
        double safeRating = clamp(ratingAvg, 1.0, 5.0);
        double count = Math.max(0, ratingCount);
        return (policy.qualityPriorCount() * policy.qualityPriorMean() + safeRating * count)
                / (policy.qualityPriorCount() + count);
    }

    private void validateArguments(
            RecommendationCandidate candidate,
            ScoringContext context,
            Instant now
    ) {
        if (candidate == null) {
            throw new InvalidScoringException("candidate is required");
        }
        if (context == null) {
            throw new InvalidScoringException("scoring context is required");
        }
        if (now == null) {
            throw new InvalidScoringException("now is required");
        }
        candidate.validate();
        context.validate();
    }

    private static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    private record Proximity(Double distanceKm, double normalized) {

        private static Proximity none() {
            return new Proximity(null, 0.0);
        }
    }

    private record Weights(double affinity, double quality, double proximity, double novelty) {
    }
}
