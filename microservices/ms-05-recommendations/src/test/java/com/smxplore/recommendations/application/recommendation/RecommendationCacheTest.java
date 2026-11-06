package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import com.smxplore.recommendations.domain.ports.out.GraphVersionPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationCachePort;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationCacheTest {

    private static final UUID TOURIST = UUID.fromString("77777777-7777-7777-7777-777777777701");
    private static final UUID SITE = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID CATEGORY = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");

    private final CountingGraph graph = new CountingGraph();
    private final MapCache cache = new MapCache();
    private final MutableGraphVersion version = new MutableGraphVersion();
    private final RecordingMetrics metrics = new RecordingMetrics();

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        RecommendationPolicy policy = policy();
        service = new RecommendationService(graph, new RecommendationScorer(policy),
                new DiversitySelector(policy), policy, cache, version, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void servesTheSecondRequestOfTheSameTouristFromTheCache() {
        StepVerifier.create(service.handle(request())).assertNext(recommendation -> {
            assertThat(recommendation.touristId()).isEqualTo(TOURIST);
        }).verifyComplete();

        StepVerifier.create(service.handle(request()))
                .assertNext(recommendation -> assertThat(recommendation.items()).hasSize(1))
                .verifyComplete();

        assertThat(graph.calls.get()).isEqualTo(1);
        assertThat(cache.entries).hasSize(1);
        assertThat(metrics.cacheMisses()).isEqualTo(1);
        assertThat(metrics.cacheHits()).isEqualTo(1);
        assertThat(metrics.items()).containsExactly(1, 1);
        assertThat(metrics.categories()).containsExactly(1, 1);
        assertThat(metrics.sites()).containsExactly(1, 1);
    }

    @Test
    void recomputesWhenTheGraphVersionChanges() {
        service.handle(request()).block();
        long firstVersion = version.value.get();

        version.value.set(firstVersion + 1);
        service.handle(request()).block();

        assertThat(graph.calls.get()).isEqualTo(2);
        assertThat(cache.entries).hasSize(2);
    }

    @Test
    void computesTheRecommendationWhenTheCacheFails() {
        cache.failReads = true;

        StepVerifier.create(service.handle(request()))
                .assertNext(recommendation -> assertThat(recommendation.items()).hasSize(1))
                .verifyComplete();

        assertThat(graph.calls.get()).isEqualTo(1);
    }

    @Test
    void keepsTheRecommendationWhenTheCacheCannotStoreIt() {
        cache.failWrites = true;

        StepVerifier.create(service.handle(request()))
                .assertNext(recommendation -> assertThat(recommendation.items()).hasSize(1))
                .verifyComplete();

        assertThat(cache.entries).isEmpty();
    }

    private static RecommendationRequest request() {
        return RecommendationRequest.builder().requesterId(TOURIST).limit(5).build();
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

    private static final class CountingGraph implements RecommendationGraphPort {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Flux<RecommendationCandidate> findCandidatesForTourist(
                UUID touristId, UUID siteId, com.smxplore.recommendations.domain.model.geo.GeoLocation location,
                int limit, Instant now
        ) {
            calls.incrementAndGet();
            return Flux.just(candidate());
        }

        @Override
        public Flux<RecommendationCandidate> findFallbackCandidates(
                UUID touristId, UUID siteId, com.smxplore.recommendations.domain.model.geo.GeoLocation location,
                int limit, Instant now
        ) {
            return Flux.empty();
        }

        @Override
        public Mono<Boolean> upsertPreference(UUID touristId, UUID categoryId, String language, Instant now) {
            return Mono.just(false);
        }

        @Override
        public Mono<Void> deletePreference(UUID touristId, UUID categoryId) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> recordView(
                UUID touristId, ItemType itemType, UUID itemId, String language, Instant now
        ) {
            return Mono.just(false);
        }

        private static RecommendationCandidate candidate() {
            return RecommendationCandidate.builder()
                    .itemType(ItemType.SITE)
                    .itemId(UUID.randomUUID())
                    .name("Sitio de prueba")
                    .categoryId(CATEGORY)
                    .categoryName("Categoría")
                    .siteId(SITE)
                    .affinity(1.0)
                    .ratingAvg(4.5)
                    .ratingCount(10)
                    .latitude(10.39)
                    .longitude(-75.47)
                    .origin(CandidateOrigin.AFFINITY)
                    .build();
        }
    }

    private static final class MapCache implements RecommendationCachePort {

        private final Map<String, Recommendation> entries = new HashMap<>();
        private boolean failReads;
        private boolean failWrites;

        @Override
        public Mono<Optional<Recommendation>> get(String key) {
            if (failReads) {
                return Mono.error(new IllegalStateException("redis caido"));
            }
            return Mono.just(Optional.ofNullable(entries.get(key)));
        }

        @Override
        public Mono<Void> put(String key, Recommendation recommendation) {
            if (failWrites) {
                return Mono.error(new IllegalStateException("redis caido"));
            }
            entries.put(key, recommendation);
            return Mono.empty();
        }
    }

    private static final class MutableGraphVersion implements GraphVersionPort {

        private final AtomicLong value = new AtomicLong(1L);

        @Override
        public Mono<Long> current() {
            return Mono.just(value.get());
        }

        @Override
        public Mono<Void> bump() {
            value.incrementAndGet();
            return Mono.empty();
        }
    }

}
