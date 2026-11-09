package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Review as the public catalog shows it.
 *
 * <p>It deliberately excludes the AI assessment ({@link ReviewAiAssessmentResponse}): the verdict of
 * the abuse filter, its model, score and categories are internal moderation data. The
 * administrative view is {@link AdminReviewResponse}.</p>
 *
 * <p>It also excludes {@code touristId}: the public view only shows the display name, so an
 * anonymous reader cannot collect stable tourist identifiers (auditoria final 2026-11-08). The
 * authoritative identifier stays in {@link AdminReviewResponse} and in the review events.</p>
 */
public record ReviewResponse(
        UUID reviewId,
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
        Instant createdAt,
        Instant updatedAt
) {
}
