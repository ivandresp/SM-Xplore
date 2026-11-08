package com.smxplore.recommendations.infrastructure.adapters.out.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.ports.out.InteractionDeduplicationPort;
import com.smxplore.recommendations.infrastructure.config.RecommendationQuotaProperties;

import java.util.UUID;

/**
 * Bounded Caffeine set of recently seen views (MS05-04). Entries expire after
 * {@code app.recommendations.quota.view-dedupe-window}, so memory is bounded by
 * {@code app.recommendations.quota.max-entries} and by the window itself.
 */
public class InMemoryInteractionDeduplicationAdapter implements InteractionDeduplicationPort {

    private final Cache<String, Boolean> seen;

    public InMemoryInteractionDeduplicationAdapter(RecommendationQuotaProperties properties) {
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(properties.getViewDedupeWindow())
                .maximumSize(properties.getMaxEntries())
                .build();
    }

    @Override
    public boolean isDuplicate(UUID touristId, ItemType itemType, UUID itemId) {
        if (touristId == null || itemType == null || itemId == null) {
            return false;
        }
        String key = touristId + "|" + itemType + "|" + itemId;
        return seen.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    }
}
