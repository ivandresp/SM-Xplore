package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReputationResponse(
        ItemType itemType,
        UUID itemId,
        double averageRating,
        long reviewCount,
        long responseCount,
        double responseRate,
        long hiddenCount,
        Map<Integer, Long> ratingDistribution,
        Instant lastReviewAt
) {
}
