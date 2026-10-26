package com.smxplore.recommendations.domain.service;

import com.smxplore.recommendations.domain.model.GeoLocation;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.RecommendationSignals;
import com.smxplore.recommendations.domain.model.ScoredCandidate;
import com.smxplore.recommendations.domain.model.ScoringContext;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure domain scorer. It receives already-filtered candidates and combines
 * affinity, review quality, proximity and novelty. It never uses reservations.
 */
public class RecommendationScorer {

    private final RecommendationPolicy policy;

    public RecommendationScorer(RecommendationPolicy policy) {
        this.policy = policy;
    }

    public ScoredCandidate score(RecommendationCandidate candidate, ScoringContext context, Instant now) {
        double affinity = normalizeAffinity(candidate.affinity(), context.maxAffinity());
        double quality = normalizeQuality(bayesianRating(candidate.ratingAvg(), candidate.ratingCount()));

        Double distanceKm = null;
        double proximity = 0.0;
        GeoLocation candidateLocation = candidate.locationOrNull();
        if (context.location() != null && candidateLocation != null) {
            distanceKm = candidateLocation.distanceKmTo(context.location());
            proximity = Math.exp(-distanceKm / policy.proximityRadiusKm());
        }

        double novelty = novelty(candidate.updatedAt(), now);

        double affinityWeight = context.hasAffinitySignals() ? policy.affinityWeight() : 0.0;
        double proximityWeight = context.location() != null ? policy.proximityWeight() : 0.0;
        double qualityWeight = policy.qualityWeight();
        double noveltyWeight = policy.noveltyWeight();
        double weightSum = affinityWeight + qualityWeight + proximityWeight + noveltyWeight;

        double score = weightSum <= 0.0
                ? 0.0
                : (affinityWeight * affinity
                + qualityWeight * quality
                + proximityWeight * proximity
                + noveltyWeight * novelty) / weightSum;

        return new ScoredCandidate(
                candidate,
                clamp(score, 0.0, 1.0),
                new RecommendationSignals(affinity, quality, distanceKm, novelty)
        );
    }

    /**
     * Bayesian average with a configurable prior. This prevents an item with a
     * single 5-star review from outranking an item with many good reviews.
     */
    public double bayesianRating(double ratingAvg, long ratingCount) {
        double safeRating = clamp(ratingAvg, 1.0, 5.0);
        double count = Math.max(0, ratingCount);
        return (policy.qualityPriorCount() * policy.qualityPriorMean()
                + safeRating * count)
                / (policy.qualityPriorCount() + count);
    }

    private static double normalizeAffinity(double affinity, double maxAffinity) {
        if (maxAffinity <= 0.0 || affinity <= 0.0) {
            return 0.0;
        }
        return clamp(affinity / maxAffinity, 0.0, 1.0);
    }

    private static double normalizeQuality(double bayesianRating) {
        return clamp((bayesianRating - 1.0) / 4.0, 0.0, 1.0);
    }

    private double novelty(Instant updatedAt, Instant now) {
        if (updatedAt == null) {
            return 0.5;
        }
        double days = Math.max(0.0, Duration.between(updatedAt, now).toMinutes() / 1440.0);
        return Math.exp(-days / policy.noveltyHalfLifeDays());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
