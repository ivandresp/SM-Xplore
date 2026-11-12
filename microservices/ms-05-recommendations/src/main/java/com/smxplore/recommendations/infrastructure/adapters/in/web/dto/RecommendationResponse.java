package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body of {@code GET /api/v1/recommendations}.
 *
 * <p>MS05-11 adds the pagination window on top of the existing payload: {@code page} is zero
 * based, {@code size} is the requested page length, {@code total} is the number of diversified
 * recommendations generated for the query, {@code totalPages = ceil(total / size)} and
 * {@code hasNext} tells the client whether requesting {@code page + 1} can return more items.</p>
 */
public record RecommendationResponse(
        UUID touristId,
        Instant generatedAt,
        RecommendationSource source,
        List<RecommendationItemResponse> items,
        int page,
        int size,
        int total,
        int totalPages,
        boolean hasNext
) {
}
