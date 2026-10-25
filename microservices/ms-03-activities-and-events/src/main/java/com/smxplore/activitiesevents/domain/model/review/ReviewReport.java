package com.smxplore.activitiesevents.domain.model.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewReport(
        UUID id,
        UUID reviewId,
        UUID reporterId,
        String reason,
        ReviewReportStatus status,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
    public static ReviewReport pending(UUID reviewId, UUID reporterId, String reason) {
        return new ReviewReport(UUID.randomUUID(), reviewId, reporterId, reason,
                ReviewReportStatus.PENDING, null, null, Instant.now());
    }

    public ReviewReport resolve(ReviewReportStatus target, UUID resolverId) {
        return new ReviewReport(id, reviewId, reporterId, reason, target, resolverId, Instant.now(), createdAt);
    }
}
