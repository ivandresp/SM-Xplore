package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReviewReportRequest(
        @NotNull ReviewReportStatus status,
        @Size(max = ReviewReport.MAX_RESOLUTION_LENGTH) String resolution
) {
}
