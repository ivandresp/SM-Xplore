package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewView(
        String id,
        String targetType,
        UUID targetId,
        UUID touristId,
        int rating,
        String comment,
        ReviewResponseView response,
        String moderationStatus,
        boolean reported,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewView from(Review review) {
        return new ReviewView(
                review.id(),
                review.targetType().code(),
                review.targetId(),
                review.touristId(),
                review.rating(),
                review.comment(),
                ReviewResponseView.from(review.response()),
                review.moderationStatus().name(),
                review.isReported(),
                review.createdAt(),
                review.updatedAt());
    }
}
