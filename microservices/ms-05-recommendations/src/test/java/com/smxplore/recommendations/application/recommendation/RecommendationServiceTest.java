package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidLimitException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final UUID TOURIST = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final UUID OTHER_TOURIST = UUID.fromString("66666666-6666-6666-6666-666666666602");
    private static final UUID ADMIN = UUID.fromString("99999999-9999-9999-9999-999999999999");
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

        mockCandidates(TOURIST, null, affinity, fallback);

        Recommendation recommendation = service.handle(request(TOURIST, 2)).block();

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

        mockCandidates(TOURIST, null, null, fallback);

        Recommendation recommendation = service.handle(request(TOURIST, 20)).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.source()).isEqualTo(RecommendationSource.FALLBACK);
        assertThat(recommendation.items()).hasSize(1);
    }

    @Test
    void usesNearbyWhenFallbackHasLocation() {
        RecommendationCandidate fallback = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        mockCandidates(TOURIST, new GeoLocation(10.39, -75.48), null, fallback);

        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(TOURIST)
                .latitude(10.39)
                .longitude(-75.48)
                .limit(20)
                .build();
        request.validate();

        Recommendation recommendation = service.handle(request).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.source()).isEqualTo(RecommendationSource.NEARBY);
    }

    @Test
    void removesFallbackDuplicates() {
        RecommendationCandidate shared = candidate(UUID.randomUUID(), CATEGORY_A, SITE_A,
                CandidateOrigin.AFFINITY, 2.0);
        RecommendationCandidate other = candidate(UUID.randomUUID(), CATEGORY_B, SITE_B,
                CandidateOrigin.FALLBACK, 0.0);

        mockCandidates(TOURIST, null, shared, shared, other);

        Recommendation recommendation = service.handle(request(TOURIST, 2)).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.items()).hasSize(2);
        assertThat(recommendation.items()).extracting(item -> item.itemId())
                .containsExactlyInAnyOrder(shared.itemId(), other.itemId());
    }

    @Test
    void returnsEmptyRecommendationWhenGraphHasNoItems() {
        mockCandidates(TOURIST, null, null);

        StepVerifier.create(service.handle(request(TOURIST, 20)))
                .assertNext(recommendation -> {
                    assertThat(recommendation.items()).isEmpty();
                    assertThat(recommendation.source()).isEqualTo(RecommendationSource.FALLBACK);
                })
                .verifyComplete();
    }

    @Test
    void adminCanQueryAnotherTourist() {
        RecommendationCandidate candidate = candidate(UUID.randomUUID(), CATEGORY_A, SITE_A,
                CandidateOrigin.AFFINITY, 2.0);
        when(graphPort.findCandidatesForTourist(eq(OTHER_TOURIST), isNull(), isNull(), anyInt(),
                any(Instant.class))).thenReturn(Flux.just(candidate));
        when(graphPort.findFallbackCandidates(eq(OTHER_TOURIST), isNull(), isNull(), anyInt(),
                any(Instant.class))).thenReturn(Flux.empty());

        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(ADMIN)
                .admin(true)
                .requestedTouristId(OTHER_TOURIST)
                .limit(20)
                .build();
        request.validate();

        Recommendation recommendation = service.handle(request).block();

        assertThat(recommendation).isNotNull();
        assertThat(recommendation.touristId()).isEqualTo(OTHER_TOURIST);
    }

    @Test
    void touristCannotQueryAnotherTourist() {
        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(TOURIST)
                .requestedTouristId(OTHER_TOURIST)
                .limit(20)
                .build();
        request.validate();

        assertThatThrownBy(() -> service.handle(request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rejectsLimitAbovePolicyMaximum() {
        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(TOURIST)
                .limit(100)
                .build();
        request.validate();

        assertThatThrownBy(() -> service.handle(request))
                .isInstanceOf(InvalidLimitException.class);
    }

    private void mockCandidates(
            UUID touristId,
            GeoLocation location,
            RecommendationCandidate affinity
    ) {
        mockCandidates(touristId, location, affinity, new RecommendationCandidate[0]);
    }

    private void mockCandidates(
            UUID touristId,
            GeoLocation location,
            RecommendationCandidate affinity,
            RecommendationCandidate... fallback
    ) {
        when(graphPort.findCandidatesForTourist(eq(touristId), isNull(),
                location == null ? isNull() : eq(location), anyInt(), any(Instant.class)))
                .thenReturn(affinity == null ? Flux.empty() : Flux.just(affinity));
        when(graphPort.findFallbackCandidates(eq(touristId), isNull(),
                location == null ? isNull() : eq(location), anyInt(), any(Instant.class)))
                .thenReturn(Flux.just(fallback));
    }

    private static RecommendationRequest request(UUID touristId, int limit) {
        RecommendationRequest request = RecommendationRequest.builder()
                .requesterId(touristId)
                .limit(limit)
                .build();
        request.validate();
        return request;
    }

    private static RecommendationCandidate candidate(
            UUID itemId,
            UUID categoryId,
            UUID siteId,
            CandidateOrigin origin,
            double affinity
    ) {
        RecommendationCandidate candidate = RecommendationCandidate.builder()
                .itemType(ItemType.EVENT)
                .itemId(itemId)
                .name("Item " + itemId)
                .categoryId(categoryId)
                .categoryName("Categoría")
                .siteId(siteId)
                .latitude(10.3910)
                .longitude(-75.4790)
                .updatedAt(NOW.minusSeconds(3600))
                .ratingAvg(4.6)
                .ratingCount(100)
                .affinity(affinity)
                .origin(origin)
                .build();
        candidate.validate();
        return candidate;
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
                .maxCategoryRatio(0.4)
                .affinityWeight(0.45)
                .qualityWeight(0.25)
                .proximityWeight(0.20)
                .noveltyWeight(0.10)
                .build();
        policy.validate();
        return policy;
    }
}
