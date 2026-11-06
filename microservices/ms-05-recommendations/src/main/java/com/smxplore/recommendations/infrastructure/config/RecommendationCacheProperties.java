package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendations.cache")
public class RecommendationCacheProperties {

    /**
     * Enables the in-process and Redis layers. When disabled every read is a miss.
     */
    private boolean enabled = true;
    /**
     * Time to live of the in-process layer, which keeps a hot result out of Redis.
     */
    private Duration localTtl = Duration.ofSeconds(10);
    /**
     * Time to live of the Redis layer, shared by every instance.
     */
    private Duration redisTtl = Duration.ofMinutes(1);
    private long localMaxSize = 1000;
    private String keyPrefix = "ms05:recommendations:";
    private String versionKey = "ms05:graph:version";

    @PostConstruct
    public void validate() {
        if (localTtl == null || localTtl.isNegative() || localTtl.isZero()) {
            throw new IllegalStateException("app.recommendations.cache.local-ttl must be positive");
        }
        if (redisTtl == null || redisTtl.isNegative() || redisTtl.isZero()) {
            throw new IllegalStateException("app.recommendations.cache.redis-ttl must be positive");
        }
        if (localMaxSize < 1) {
            throw new IllegalStateException("app.recommendations.cache.local-max-size must be >= 1");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("app.recommendations.cache.key-prefix is required");
        }
        if (versionKey == null || versionKey.isBlank()) {
            throw new IllegalStateException("app.recommendations.cache.version-key is required");
        }
    }
}
