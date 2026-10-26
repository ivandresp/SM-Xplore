package com.smxplore.recommendations.domain.service;

import com.smxplore.recommendations.domain.model.CandidateOrigin;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.RecommendationSignals;
import com.smxplore.recommendations.domain.model.ScoredCandidate;
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
        RecommendationCandidate candidate = new RecommendationCandidate(
                ItemType.EVENT,
                UUID.randomUUID(),
                "Item",
                categoryId,
                "Categoría",
                siteId,
                null,
                null,
                null,
                null,
                Instant.now(),
                4.5,
                10,
                0.0,
                CandidateOrigin.FALLBACK
        );
        return new ScoredCandidate(candidate, score, new RecommendationSignals(0, 0.5, null, 0.5));
    }

    private static RecommendationPolicy policy() {
        return new RecommendationPolicy(
                20, 50, 200, 2, 10.0, 90.0, 90.0, 3.5, 10.0, 2, 0.5,
                0.45, 0.25, 0.20, 0.10
        );
    }
}
