package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class ReviewReport {

    public static final int MAX_DETAILS_LENGTH = 500;
    public static final int MAX_RESOLUTION_LENGTH = 500;

    private final UUID reportId;
    private final UUID reviewId;
    private final ItemType itemType;
    private final UUID itemId;
    private final UUID reportedBy;
    private final String reportedByName;
    private final ReviewReportReason reason;
    private final String details;
    @Builder.Default
    private final ReviewReportStatus status = ReviewReportStatus.OPEN;
    private final Instant createdAt;
    private final Instant resolvedAt;
    private final UUID resolvedBy;
    private final String resolution;

    public void validate() {
        requireNonNull(reportId, "review report reportId is required");
        requireNonNull(reviewId, "review report reviewId is required");
        requireNonNull(itemType, "review report itemType is required");
        requireNonNull(itemId, "review report itemId is required");
        requireNonNull(reportedBy, "review report reportedBy is required");
        requireNonNull(reason, "review report reason is required");
        requireNonNull(createdAt, "review report createdAt is required");
        validateText(details, MAX_DETAILS_LENGTH, "review report details");
        validateText(resolution, MAX_RESOLUTION_LENGTH, "review report resolution");
        validateStatus();
    }

    private void validateStatus() {
        if (status == null) {
            throw new InvalidCatalogException("review report status is required");
        }
        if (status != ReviewReportStatus.OPEN) {
            requireNonNull(resolvedAt, "review report resolvedAt is required when resolved");
            requireNonNull(resolvedBy, "review report resolvedBy is required when resolved");
        }
    }

    private static void validateText(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidCatalogException(field + " cannot exceed " + maxLength + " characters");
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new InvalidCatalogException(message);
        }
    }
}
