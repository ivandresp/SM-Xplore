package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Review plus the internal moderation data, for the administrative endpoints only. */
public record AdminReviewResponse(
        UUID reviewId,
        UUID touristId,
        String touristName,
        ItemType itemType,
        UUID itemId,
        String itemName,
        int rating,
        String commentary,
        List<String> photosUrls,
        String response,
        Instant respondedAt,
        int helpfulReactions,
        ReviewStatus status,
        ReviewAiAssessmentResponse ai,
        Instant createdAt,
        Instant updatedAt
) {
}
