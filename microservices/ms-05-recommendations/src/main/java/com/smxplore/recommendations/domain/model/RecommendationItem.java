package com.smxplore.recommendations.domain.model;

import java.util.UUID;

public record RecommendationItem(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID categoryId,
        UUID siteId,
        double score,
        String reason,
        RecommendationSignals signals
) {
}
