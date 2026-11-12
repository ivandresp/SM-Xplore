package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * MS05-11: startup warmup of the recommendation dependencies.
 *
 * <p>The runner issues one cheap Cypher count and the fallback query so the Neo4j driver pool,
 * the Cypher plan cache and the Redis connection are ready before the first tourist request. It
 * is enabled by default; the {@code test} profile disables it so Testcontainers startup is not
 * delayed, and an environment with no Redis/Neo4j can override
 * {@code RECOMMENDATIONS_WARMUP_ENABLED=false}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.warmup")
public class RecommendationWarmupProperties {

    private boolean enabled = true;

    /**
     * Maximum time the application waits for the warmup. A timeout only logs a warning: the API
     * starts anyway and the first request pays the cold-start cost.
     */
    private Duration timeout = Duration.ofSeconds(30);

    @PostConstruct
    public void validate() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("app.warmup.timeout must be positive");
        }
    }
}
