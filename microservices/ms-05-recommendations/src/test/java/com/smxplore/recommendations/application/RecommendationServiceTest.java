package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.model.CandidateOrigin;
import com.smxplore.recommendations.domain.model.GeoLocation;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.model.Recommendation;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.RecommendationQuery;
import com.smxplore.recommendations.domain.model.RecommendationSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.service.DiversitySelector;
import com.smxplore.recommendations.domain.service.RecommendationScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final UUID TOURIST = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final UUID CATEGORY_A = UUID.fromString("11111111-1111-1111-1111-111111111106");
    private static final UUID CATEGORY_B = UUID.fromString("11111111-1111-1111-1111-111111111107");
    private static final UUID SITE_A = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID SITE_B = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private RecommendationGraphPort graphPort;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        RecommendationPolicy policy = policy();
        service = new RecommendationService(
                graphPort,
                new RecommendationScorer(policy),
                new DiversitySelector(policy),
                policy,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void combinesAffinityAndFallbackWhenAffinityIsNotEnough() {
        RecommendationCandidate affinity = candidate(UUID.randomUUID(), CATEGORY_A, SITE_A,
                CandidateOrigin.AFFINITY, 2.0);
        RecommendationCandidate fallback = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        when(graphPort.findCandidatesForTourist(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(affinity));
        when(graphPort.findFallbackCandidates(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(fallback));

        Recommendation recommendation = service.handle(new RecommendationQuery(TOURIST, null, null, 2)).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.source()).isEqualTo(RecommendationSource.MIXED);
        assertThat(recommendation.items()).hasSize(2);
        assertThat(recommendation.items()).extracting(item -> item.itemId())
                .containsExactlyInAnyOrder(affinity.itemId(), fallback.itemId());
    }

    @Test
    void usesFallbackWhenThereAreNoAffinityCandidates() {
        RecommendationCandidate fallback = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        when(graphPort.findCandidatesForTourist(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.empty());
        when(graphPort.findFallbackCandidates(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(fallback));

        Recommendation recommendation = service.handle(new RecommendationQuery(TOURIST, null, null, 20)).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.source()).isEqualTo(RecommendationSource.FALLBACK);
        assertThat(recommendation.items()).hasSize(1);
    }

    @Test
    void usesNearbyWhenFallbackHasLocation() {
        RecommendationCandidate fallback = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        when(graphPort.findCandidatesForTourist(eq(TOURIST), isNull(), any(GeoLocation.class), anyInt(),
                any(Instant.class))).thenReturn(Flux.empty());
        when(graphPort.findFallbackCandidates(eq(TOURIST), isNull(), any(GeoLocation.class), anyInt(),
                any(Instant.class))).thenReturn(Flux.just(fallback));

        Recommendation recommendation = service.handle(
                new RecommendationQuery(TOURIST, null, new GeoLocation(10.39, -75.48), 20)
        ).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.source()).isEqualTo(RecommendationSource.NEARBY);
    }

    @Test
    void removesFallbackDuplicates() {
        RecommendationCandidate shared = candidate(UUID.randomUUID(), CATEGORY_A, SITE_A,
                CandidateOrigin.AFFINITY, 2.0);
        RecommendationCandidate other = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        when(graphPort.findCandidatesForTourist(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(shared));
        when(graphPort.findFallbackCandidates(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(shared, other));

        Recommendation recommendation = service.handle(new RecommendationQuery(TOURIST, null, null, 2)).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.items()).hasSize(2);
        assertThat(recommendation.items()).extracting(item -> item.itemId())
                .containsExactlyInAnyOrder(shared.itemId(), other.itemId());
    }

    @Test
    void returnsEmptyRecommendationWhenGraphHasNoItems() {
        when(graphPort.findCandidatesForTourist(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.empty());
        when(graphPort.findFallbackCandidates(eq(TOURIST), isNull(), isNull(), anyInt(), any(Instant.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.handle(new RecommendationQuery(TOURIST, null, null, 20)))
                .assertNext(recommendation -> {
                    assertThat(recommendation.items()).isEmpty();
                    assertThat(recommendation.source()).isEqualTo(RecommendationSource.FALLBACK);
                })
                .verifyComplete();
    }

    private static RecommendationCandidate candidate(
            UUID itemId,
            UUID categoryId,
            UUID siteId,
            CandidateOrigin origin,
            double affinity
    ) {
        return new RecommendationCandidate(
                ItemType.EVENT,
                itemId,
                "Item " + itemId,
                categoryId,
                "Categoría",
                siteId,
                10.3910,
                -75.4790,
                null,
                null,
                NOW.minusSeconds(3600),
                4.6,
                100,
                affinity,
                origin
        );
    }

    private static RecommendationPolicy policy() {
        return new RecommendationPolicy(
                20, 50, 200, 2, 10.0, 90.0, 90.0, 3.5, 10.0, 2, 0.4,
                0.45, 0.25, 0.20, 0.10
        );
    }
}
