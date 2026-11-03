package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportReason;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ReviewReportResponse(
        UUID reportId,
        UUID reviewId,
        ItemType itemType,
        UUID itemId,
        UUID reportedBy,
        String reportedByName,
        ReviewReportReason reason,
        String details,
        ReviewReportStatus status,
        Instant createdAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolution
) {
}
