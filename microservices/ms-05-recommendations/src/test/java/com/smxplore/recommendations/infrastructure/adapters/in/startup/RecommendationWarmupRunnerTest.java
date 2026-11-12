package com.smxplore.recommendations.infrastructure.adapters.in.startup;

import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.infrastructure.config.RecommendationCacheProperties;
import com.smxplore.recommendations.infrastructure.config.RecommendationWarmupProperties;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MS05-11: warmup is best-effort. If Neo4j is not reachable when the application starts, the
 * runner logs a warning and lets the service boot; the first request pays the cold start.
 */
class RecommendationWarmupRunnerTest {

    @Test
    void swallowsAnUnavailableNeo4jSoTheApplicationStillStarts() {
        Driver driver = mock(Driver.class);
        when(driver.session(ReactiveSession.class))
                .thenThrow(new RuntimeException("neo4j is not reachable"));
        RecommendationWarmupProperties warmupProperties = new RecommendationWarmupProperties();
        warmupProperties.setTimeout(Duration.ofMillis(200));
        RecommendationWarmupRunner runner = new RecommendationWarmupRunner(
                driver,
                mock(RecommendationGraphPort.class),
                mock(ReactiveStringRedisTemplate.class),
                new RecommendationCacheProperties(),
                warmupProperties,
                mock(RecommendationPolicy.class),
                Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC));

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
