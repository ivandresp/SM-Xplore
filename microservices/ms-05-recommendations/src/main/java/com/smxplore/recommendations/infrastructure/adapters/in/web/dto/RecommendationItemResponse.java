package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import com.smxplore.recommendations.domain.model.ItemType;

import java.util.UUID;

public record RecommendationItemResponse(
        ItemType itemType,
        UUID itemId,
        String name,
        UUID categoryId,
        UUID siteId,
        double score,
        String reason,
        RecommendationSignalsResponse signals
) {
}
