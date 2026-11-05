package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Review plus the internal moderation data, for the administrative endpoints only. Never returned
 * by the public catalog endpoints.
 */
public record AdminReviewResponse(
        UUID id,
        UUID itemId,
        ReviewItemType itemType,
        ReviewResponse.ReviewUserResponse user,
        int rating,
        String comment,
        ReviewResponse.ReviewReplyResponse reply,
        ReviewStatus status,
        ReviewAiAssessmentResponse ai,
        boolean aiRetained,
        Instant createdAt,
        Instant updatedAt
) {
}
