package com.smxplore.recommendations.infrastructure.adapters.out.redis;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression tests for MS05-05: Redis is a shared resource, so a cached entry is only trusted
 * when it parses, belongs to the key it was stored under and looks like a recommendation.
 */
@ExtendWith(MockitoExtension.class)
class RedisRecommendationCacheAdapterTest {

    private static final UUID TOURIST = UUID.fromString("77777777-7777-7777-7777-777777777701");
    private static final UUID OTHER_TOURIST = UUID.fromString("77777777-7777-7777-7777-777777777702");
    private static final String KEY = "v1:" + TOURIST + ":-:-:-:20";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationCacheProperties properties = new RecommendationCacheProperties();

    private RedisRecommendationCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisRecommendationCacheAdapter(redisTemplate, objectMapper, properties);
    }

    @Test
    void discardsAnEntryThatIsNotJson() {
        stubRedis("esto no es json");

        StepVerifier.create(adapter.get(KEY))
                .assertNext(optional -> assertThat(optional).isEmpty())
                .verifyComplete();
    }

    @Test
    void discardsAnOversizedEntry() {
        stubRedis("x".repeat(70_000));

        StepVerifier.create(adapter.get(KEY))
                .assertNext(optional -> assertThat(optional).isEmpty())
                .verifyComplete();
    }

    @Test
    void discardsAnEntryThatBelongsToAnotherTourist() {
        stubRedis(json(recommendation(OTHER_TOURIST, 0.5)));

        StepVerifier.create(adapter.get(KEY))
                .assertNext(optional -> assertThat(optional).isEmpty())
                .verifyComplete();
    }

    @Test
    void discardsAnEntryWithAScoreOutsideThePolicyRange() {
        stubRedis(json(recommendation(TOURIST, 2.5)));

        StepVerifier.create(adapter.get(KEY))
                .assertNext(optional -> assertThat(optional).isEmpty())
                .verifyComplete();
    }

    @Test
    void returnsAValidEntry() {
        stubRedis(json(recommendation(TOURIST, 0.5)));

        StepVerifier.create(adapter.get(KEY))
                .assertNext(optional -> {
                    assertThat(optional).isPresent();
                    assertThat(optional.map(Recommendation::touristId)).contains(TOURIST);
                })
                .verifyComplete();
    }

    private void stubRedis(String value) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.just(value));
    }

    private String json(Recommendation recommendation) {
        return objectMapper.writeValueAsString(recommendation);
    }

    private static Recommendation recommendation(UUID touristId, double score) {
        RecommendationItem item = RecommendationItem.builder()
                .itemType(ItemType.SITE)
                .itemId(UUID.randomUUID())
                .name("Playa Cristal")
                .categoryId(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .score(score)
                .reason("Afinidad con tus preferencias")
                .signals(RecommendationSignals.builder()
                        .affinity(0.5)
                        .quality(0.7)
                        .distanceKm(2.0)
                        .novelty(0.6)
                        .build())
                .build();
        return new Recommendation(touristId, Instant.parse("2026-11-03T15:00:00Z"),
                RecommendationSource.AFFINITY, List.of(item));
    }
}
