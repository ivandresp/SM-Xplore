package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID itemId,
        ReviewItemType itemType,
        ReviewUserResponse user,
        int rating,
        String comment,
        ReviewReplyResponse reply,
        ReviewStatus status,
        ReviewAiAssessmentResponse ai,
        boolean aiRetained,
        Instant createdAt,
        Instant updatedAt
) {
    public record ReviewUserResponse(String name) {
    }

    public record ReviewReplyResponse(String message, Instant createdAt) {
    }
}
