package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ranking.ScoredCandidate;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiversitySelectorTest {

    private final DiversitySelector selector = new DiversitySelector(policy());

    @Test
    void capsItemsPerSite() {
        UUID siteA = UUID.randomUUID();
        UUID siteB = UUID.randomUUID();
        List<ScoredCandidate> ranked = List.of(
                scored(1.0, siteA, UUID.randomUUID()),
                scored(0.9, siteA, UUID.randomUUID()),
                scored(0.8, siteA, UUID.randomUUID()),
                scored(0.7, siteB, UUID.randomUUID()),
                scored(0.6, siteB, UUID.randomUUID())
        );

        List<ScoredCandidate> selected = selector.select(ranked, 4);

        assertThat(selected).hasSize(4);
        assertThat(selected.stream().filter(item -> siteA.equals(item.candidate().siteId()))).hasSize(2);
    }

    @Test
    void capsItemsPerCategory() {
        UUID categoryA = UUID.randomUUID();
        UUID categoryB = UUID.randomUUID();
        List<ScoredCandidate> ranked = List.of(
                scored(1.0, UUID.randomUUID(), categoryA),
                scored(0.9, UUID.randomUUID(), categoryA),
                scored(0.8, UUID.randomUUID(), categoryA),
                scored(0.7, UUID.randomUUID(), categoryB),
                scored(0.6, UUID.randomUUID(), categoryB)
        );

        List<ScoredCandidate> selected = selector.select(ranked, 4);

        assertThat(selected).hasSize(4);
        assertThat(selected.stream().filter(item -> categoryA.equals(item.candidate().categoryId()))).hasSize(2);
    }

    @Test
    void fillsRemainingSlotsWhenCapsCannotBeSatisfied() {
        UUID site = UUID.randomUUID();
        List<ScoredCandidate> ranked = List.of(
                scored(1.0, site, UUID.randomUUID()),
                scored(0.9, site, UUID.randomUUID()),
                scored(0.8, site, UUID.randomUUID())
        );

        List<ScoredCandidate> selected = selector.select(ranked, 3);

        assertThat(selected).hasSize(3);
    }

    @Test
    void returnsEmptyForEmptyInput() {
        assertThat(selector.select(List.of(), 10)).isEmpty();
    }

    private static ScoredCandidate scored(double score, UUID siteId, UUID categoryId) {
        RecommendationCandidate candidate = RecommendationCandidate.builder()
                .itemType(ItemType.EVENT)
                .itemId(UUID.randomUUID())
                .name("Item")
                .categoryId(categoryId)
                .categoryName("Categoría")
                .siteId(siteId)
                .updatedAt(Instant.now())
                .ratingAvg(4.5)
                .ratingCount(10)
                .affinity(0.0)
                .origin(CandidateOrigin.FALLBACK)
                .build();
        candidate.validate();

        RecommendationSignals signals = RecommendationSignals.builder()
                .affinity(0)
                .quality(0.5)
                .novelty(0.5)
                .build();
        signals.validate();

        return new ScoredCandidate(candidate, score, signals);
    }

    private static RecommendationPolicy policy() {
        RecommendationPolicy policy = RecommendationPolicy.builder()
                .defaultLimit(20)
                .maxLimit(50)
                .candidateLimit(200)
                .categoryMaxDepth(2)
                .proximityRadiusKm(10.0)
                .noveltyHalfLifeDays(90.0)
                .preferenceHalfLifeDays(90.0)
                .qualityPriorMean(3.5)
                .qualityPriorCount(10.0)
                .maxItemsPerSite(2)
                .maxCategoryRatio(0.5)
                .affinityWeight(0.45)
                .qualityWeight(0.25)
                .proximityWeight(0.20)
                .noveltyWeight(0.10)
                .build();
        policy.validate();
        return policy;
    }
}
