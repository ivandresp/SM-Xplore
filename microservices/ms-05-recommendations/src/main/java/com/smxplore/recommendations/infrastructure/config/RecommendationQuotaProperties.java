package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-user budgets for the paths that cost graph work (MS05-02 and MS05-04).
 *
 * <p>Defaults are safe in every profile: a tourist gets 120 recommendation queries per
 * minute, 60 view writes per minute and 30 preference writes per minute, which is far above
 * normal browsing and far below what is needed to saturate Neo4j. The top-level
 * {@code enabled} flag is the master switch; the nested limits can be disabled separately.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendations.quota")
public class RecommendationQuotaProperties {

    /** Master switch for every quota in this class. */
    private boolean enabled = true;
    private int maxRequests = 120;
    private Duration window = Duration.ofMinutes(1);
    private int maxEntries = 10_000;
    /** Window in which the same view (tourist, item type, item) is ignored instead of written. */
    private Duration viewDedupeWindow = Duration.ofMinutes(1);
    private Limit views = new Limit(60, Duration.ofMinutes(1));
    private Limit preferences = new Limit(30, Duration.ofMinutes(1));

    @PostConstruct
    public void validate() {
        validateLimit("app.recommendations.quota", maxRequests, window);
        if (maxEntries < 1) {
            throw new IllegalStateException("app.recommendations.quota.max-entries must be >= 1");
        }
        if (viewDedupeWindow == null || viewDedupeWindow.isNegative() || viewDedupeWindow.isZero()) {
            throw new IllegalStateException(
                    "app.recommendations.quota.view-dedupe-window must be positive");
        }
        validateLimit("app.recommendations.quota.views", views);
        validateLimit("app.recommendations.quota.preferences", preferences);
    }

    private static void validateLimit(String prefix, Limit limit) {
        if (limit == null) {
            throw new IllegalStateException(prefix + " must be configured");
        }
        validateLimit(prefix, limit.getMaxRequests(), limit.getWindow());
    }

    private static void validateLimit(String prefix, int maxRequests, Duration window) {
        if (maxRequests < 1) {
            throw new IllegalStateException(prefix + ".max-requests must be >= 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException(prefix + ".window must be positive");
        }
    }

    @Getter
    @Setter
    public static class Limit {

        private boolean enabled = true;
        private int maxRequests;
        private Duration window;

        public Limit() {
        }

        public Limit(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }
    }
}
