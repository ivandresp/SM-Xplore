package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ReviewResponse;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponseView(UUID responderId, String text, Instant createdAt, Instant updatedAt) {

    public static ReviewResponseView from(ReviewResponse response) {
        return response == null
                ? null
                : new ReviewResponseView(
                        response.responderId(), response.text(), response.createdAt(), response.updatedAt());
    }
}
