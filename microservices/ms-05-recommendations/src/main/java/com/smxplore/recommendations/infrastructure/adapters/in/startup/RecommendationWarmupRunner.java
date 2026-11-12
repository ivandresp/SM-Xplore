package com.smxplore.recommendations.infrastructure.adapters.in.startup;

import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import com.smxplore.recommendations.infrastructure.config.RecommendationWarmupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

/**
 * MS05-11: warms Neo4j and Redis at startup.
 *
 * <p>It runs after the schema/seed initializers ({@link Order} 2), executes one cheap Cypher count
 * of published catalog items, the fallback candidate query and one Redis read (the graph version
 * key). Failures are logged and swallowed: the warmup is an optimization, not a readiness gate,
 * so a cold dependency delays the first request instead of preventing the service from starting.
 * It can be disabled with {@code RECOMMENDATIONS_WARMUP_ENABLED=false}; the test profile disables
 * it explicitly.</p>
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationWarmupRunner implements ApplicationRunner {

    /** Synthetic tourist with no graph data: the queries are read-only and return no items. */
    private static final UUID WARMUP_TOURIST =
            UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private static final String PUBLISHED_ITEMS_COUNT = """
            MATCH (item:CatalogItem)
            WHERE item.status = 'PUBLISHED'
            RETURN count(item) AS total
            """;

    private final Driver driver;
    private final RecommendationGraphPort graphPort;
    private final ReactiveStringRedisTemplate redis;
    private final RecommendationCacheProperties cacheProperties;
    private final RecommendationWarmupProperties warmupProperties;
    private final RecommendationPolicy policy;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.nanoTime();
        try {
            Long publishedItems = warmNeo4j()
                    .flatMap(total -> warmRedis().thenReturn(total))
                    .block(warmupProperties.getTimeout());
            log.info("Recommendation warmup finished in {} ms (published items: {})",
                    elapsedMillis(startedAt), publishedItems);
        } catch (RuntimeException exception) {
            log.warn("Recommendation warmup failed after {} ms ({}); the service will start anyway",
                    elapsedMillis(startedAt), exception.getMessage());
        }
    }

    private Mono<Long> warmNeo4j() {
        return Mono.usingWhen(
                        Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                        session -> Flux.from(session.executeRead(tx ->
                                Flux.from(tx.run(PUBLISHED_ITEMS_COUNT))
                                        .flatMap(result -> Flux.from(result.records()))
                                        .map(record -> record.get("total").asLong())
                                        .single())).singleOrEmpty(),
                        session -> Mono.from(session.close()))
                .flatMap(total -> graphPort
                        .findCandidatesForTourist(WARMUP_TOURIST, null, null, fallbackLimit(), clock.instant())
                        .then()
                        .then(graphPort
                                .findFallbackCandidates(
                                        WARMUP_TOURIST, null, null, fallbackLimit(), clock.instant())
                                .then())
                        .thenReturn(total))
                .switchIfEmpty(Mono.just(0L));
    }

    private Mono<Void> warmRedis() {
        return redis.opsForValue().get(cacheProperties.getVersionKey()).then();
    }

    private int fallbackLimit() {
        return Math.max(1, Math.min(policy.defaultLimit(), policy.candidateLimit()));
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
