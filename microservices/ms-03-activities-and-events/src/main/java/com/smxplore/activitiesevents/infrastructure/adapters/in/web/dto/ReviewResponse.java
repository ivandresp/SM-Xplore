package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;

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
        Instant createdAt
) {
    public record ReviewUserResponse(UUID id, String name) {
    }

    public record ReviewReplyResponse(String message, UUID responderId, Instant createdAt) {
    }
}
