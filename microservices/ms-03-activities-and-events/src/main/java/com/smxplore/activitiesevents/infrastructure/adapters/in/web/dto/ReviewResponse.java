package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Review as the public catalog shows it.
 *
 * <p>It deliberately excludes the AI assessment ({@code ai}) and {@code aiRetained}: the verdict of
 * the abuse filter, its model, score and categories are internal moderation data. The
 * administrative view is {@link AdminReviewResponse}.
 */
public record ReviewResponse(
        UUID id,
        UUID itemId,
        ReviewItemType itemType,
        ReviewUserResponse user,
        int rating,
        String comment,
        ReviewReplyResponse reply,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public record ReviewUserResponse(String name) {
    }

    public record ReviewReplyResponse(String message, Instant createdAt) {
    }
}
