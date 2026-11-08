package com.smxplore.recommendations.infrastructure.adapters.out.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.recommendations.domain.exception.RateLimitExceededException;
import com.smxplore.recommendations.domain.ports.out.RecommendationQuotaPort;
import com.smxplore.recommendations.infrastructure.config.RecommendationQuotaProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Fixed-window counter per tourist held in a bounded Caffeine cache (MS05-02). The state is
 * local to the instance, which is enough because the budget protects a single API instance:
 * the gateway already limits by IP and the quota only has to stop one account from turning
 * every request into two graph queries. A distributed counter is not worth a network hop
 * here.
 */
public class InMemoryRecommendationQuotaAdapter implements RecommendationQuotaPort {

    private final RecommendationQuotaProperties properties;
    private final Clock clock;
    private final Cache<UUID, Window> windows;

    public InMemoryRecommendationQuotaAdapter(RecommendationQuotaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(properties.getWindow())
                .maximumSize(properties.getMaxEntries())
                .build();
    }

    @Override
    public void check(UUID touristId) {
        if (!properties.isEnabled()) {
            return;
        }
        if (touristId == null) {
            throw new RateLimitExceededException("A tourist identity is required to query recommendations", 1);
        }
        Window window = windows.get(touristId, key -> new Window(clock.instant()));
        synchronized (window) {
            Instant now = clock.instant();
            Instant resetAt = window.start.plus(properties.getWindow());
            if (!now.isBefore(resetAt)) {
                window.start = now;
                window.count = 0;
                resetAt = now.plus(properties.getWindow());
            }
            if (window.count >= properties.getMaxRequests()) {
                long retryAfter = Math.max(1, Duration.between(now, resetAt).toSeconds() + 1);
                throw new RateLimitExceededException(
                        "Recommendation quota exceeded: at most " + properties.getMaxRequests()
                                + " queries per " + properties.getWindow().toSeconds()
                                + " seconds; try again in " + retryAfter + " seconds",
                        retryAfter);
            }
            window.count++;
        }
    }

    private static final class Window {

        private Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }
    }
}
