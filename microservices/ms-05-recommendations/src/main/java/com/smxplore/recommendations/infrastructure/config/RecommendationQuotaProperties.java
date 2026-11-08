package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-user budget for {@code GET /api/v1/recommendations} (MS05-02). Defaults are safe in
 * every profile: a tourist gets 120 queries per minute, which is far above normal browsing
 * and far below what is needed to saturate the graph.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendations.quota")
public class RecommendationQuotaProperties {

    private boolean enabled = true;
    private int maxRequests = 120;
    private Duration window = Duration.ofMinutes(1);
    private int maxEntries = 10_000;

    @PostConstruct
    public void validate() {
        if (maxRequests < 1) {
            throw new IllegalStateException("app.recommendations.quota.max-requests must be >= 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("app.recommendations.quota.window must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalStateException("app.recommendations.quota.max-entries must be >= 1");
        }
    }
}
