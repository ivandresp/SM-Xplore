package com.smxplore.activitiesevents.domain.model.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewReport(UUID id, UUID reviewId, UUID reporterId, String reason, Instant createdAt) {
}
