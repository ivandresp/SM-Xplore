package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;

import java.util.UUID;

public record ReviewSummaryResponse(
        ItemType itemType,
        UUID itemId,
        double averageRating,
        long reviewCount
) {
}
