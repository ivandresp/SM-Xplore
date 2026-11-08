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
 * Fixed-window counters per tourist held in bounded Caffeine caches (MS05-02 and MS05-04).
 * The state is local to the instance, which is enough because the budgets protect a single
 * API instance: the gateway already limits by IP and the quotas only have to stop one account
 * from turning every request into graph work. A distributed counter is not worth a network
 * hop here.
 */
public class InMemoryRecommendationQuotaAdapter implements RecommendationQuotaPort {

    private final RecommendationQuotaProperties properties;
    private final Clock clock;
    private final Cache<UUID, Window> recommendationWindows;
    private final Cache<UUID, Window> viewWindows;
    private final Cache<UUID, Window> preferenceWindows;

    public InMemoryRecommendationQuotaAdapter(RecommendationQuotaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.recommendationWindows = newCache(properties.getMaxEntries(), properties.getWindow());
        this.viewWindows = newCache(properties.getMaxEntries(), properties.getViews().getWindow());
        this.preferenceWindows = newCache(properties.getMaxEntries(),
                properties.getPreferences().getWindow());
    }

    @Override
    public void check(UUID touristId) {
        if (!properties.isEnabled()) {
            return;
        }
        check(recommendationWindows, touristId, properties.getMaxRequests(), properties.getWindow(),
                "Recommendation");
    }

    @Override
    public void checkView(UUID touristId) {
        if (!properties.isEnabled() || !properties.getViews().isEnabled()) {
            return;
        }
        check(viewWindows, touristId, properties.getViews().getMaxRequests(),
                properties.getViews().getWindow(), "Interaction");
    }

    @Override
    public void checkPreference(UUID touristId) {
        if (!properties.isEnabled() || !properties.getPreferences().isEnabled()) {
            return;
        }
        check(preferenceWindows, touristId, properties.getPreferences().getMaxRequests(),
                properties.getPreferences().getWindow(), "Preference");
    }

    private void check(Cache<UUID, Window> windows, UUID touristId, int maxRequests, Duration window,
                       String label) {
        if (touristId == null) {
            throw new RateLimitExceededException("A tourist identity is required to use this endpoint", 1);
        }
        Window current = windows.get(touristId, key -> new Window(clock.instant()));
        synchronized (current) {
            Instant now = clock.instant();
            Instant resetAt = current.start.plus(window);
            if (!now.isBefore(resetAt)) {
                current.start = now;
                current.count = 0;
                resetAt = now.plus(window);
            }
            if (current.count >= maxRequests) {
                long retryAfter = Math.max(1, Duration.between(now, resetAt).toSeconds() + 1);
                throw new RateLimitExceededException(
                        label + " quota exceeded: at most " + maxRequests + " requests per "
                                + window.toSeconds() + " seconds; try again in " + retryAfter
                                + " seconds",
                        retryAfter);
            }
            current.count++;
        }
    }

    private static Cache<UUID, Window> newCache(int maxEntries, Duration window) {
        return Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(maxEntries)
                .build();
    }

    private static final class Window {

        private Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }
    }
}
