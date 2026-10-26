package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import com.smxplore.recommendations.domain.model.RecommendationSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID touristId,
        Instant generatedAt,
        RecommendationSource source,
        List<RecommendationItemResponse> items
) {
}
