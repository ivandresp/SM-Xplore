package com.smxplore.recommendations.domain.service;

import com.smxplore.recommendations.domain.model.CandidateOrigin;
import com.smxplore.recommendations.domain.model.GeoLocation;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ScoredCandidate;
import com.smxplore.recommendations.domain.model.ScoringContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScorerTest {

    private static final UUID CATEGORY = UUID.fromString("11111111-1111-1111-1111-111111111106");
    private static final UUID SITE = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private final RecommendationScorer scorer = new RecommendationScorer(policy());

    @Test
    void qualitySmoothingFavorsEvidence() {
        double onePerfectReview = scorer.bayesianRating(5.0, 1);
        double manyGoodReviews = scorer.bayesianRating(4.7, 200);

        assertThat(manyGoodReviews).isGreaterThan(onePerfectReview);
    }

    @Test
    void closerCandidateGetsHigherProximityScore() {
        RecommendationCandidate near = candidate(UUID.randomUUID(), 4.6, 100, 10.3910, -75.4790, NOW, 1.0);
        RecommendationCandidate far = candidate(UUID.randomUUID(), 4.6, 100, 10.5000, -75.7000, NOW, 1.0);
        ScoringContext context = new ScoringContext(new GeoLocation(10.3910, -75.4790), 1.0, true);

        ScoredCandidate nearScore = scorer.score(near, context, NOW);
        ScoredCandidate farScore = scorer.score(far, context, NOW);

        assertThat(nearScore.score()).isGreaterThan(farScore.score());
        assertThat(nearScore.signals().distanceKm()).isZero();
        assertThat(farScore.signals().distanceKm()).isPositive();
    }

    @Test
    void candidateWithoutAffinityStillScoresByQualityAndNovelty() {
        RecommendationCandidate candidate = candidate(UUID.randomUUID(), 4.6, 100, null, null, NOW, 0.0);
        ScoringContext context = new ScoringContext(null, 0.0, false);

        ScoredCandidate scored = scorer.score(candidate, context, NOW);

        assertThat(scored.signals().affinity()).isZero();
        assertThat(scored.signals().quality()).isPositive();
        assertThat(scored.signals().novelty()).isPositive();
        assertThat(scored.score()).isPositive();
    }

    @Test
    void fresherCandidateGetsHigherNovelty() {
        RecommendationCandidate fresh = candidate(UUID.randomUUID(), 4.5, 50, null, null, NOW, 0.0);
        RecommendationCandidate old = candidate(UUID.randomUUID(), 4.5, 50, null, null,
                NOW.minus(180, ChronoUnit.DAYS), 0.0);
        ScoringContext context = new ScoringContext(null, 0.0, false);

        ScoredCandidate freshScore = scorer.score(fresh, context, NOW);
        ScoredCandidate oldScore = scorer.score(old, context, NOW);

        assertThat(freshScore.signals().novelty()).isGreaterThan(oldScore.signals().novelty());
        assertThat(freshScore.score()).isGreaterThan(oldScore.score());
    }

    @Test
    void everySignalIsNormalized() {
        RecommendationCandidate candidate = candidate(UUID.randomUUID(), 5.0, 1_000, 10.4, -75.5, NOW, 10.0);
        ScoringContext context = new ScoringContext(new GeoLocation(10.4, -75.5), 20.0, true);

        ScoredCandidate scored = scorer.score(candidate, context, NOW);

        assertThat(scored.score()).isBetween(0.0, 1.0);
        assertThat(scored.signals().affinity()).isBetween(0.0, 1.0);
        assertThat(scored.signals().quality()).isBetween(0.0, 1.0);
        assertThat(scored.signals().novelty()).isBetween(0.0, 1.0);
    }

    private static RecommendationCandidate candidate(
            UUID id,
            double ratingAvg,
            long ratingCount,
            Double lat,
            Double lon,
            Instant updatedAt,
            double affinity
    ) {
        return new RecommendationCandidate(
                ItemType.EVENT, id, "Item", CATEGORY, "Música", SITE,
                lat, lon, null, null, updatedAt, ratingAvg, ratingCount, affinity,
                CandidateOrigin.AFFINITY
        );
    }

    static RecommendationPolicy policy() {
        return new RecommendationPolicy(
                20, 50, 200, 2, 10.0, 90.0, 90.0, 3.5, 10.0, 2, 0.4,
                0.45, 0.25, 0.20, 0.10
        );
    }
}
