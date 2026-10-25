package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ReviewReportResponse(
        UUID id,
        UUID reviewId,
        String reason,
        ReviewReportStatus status,
        Instant resolvedAt,
        Instant createdAt
) {
}
