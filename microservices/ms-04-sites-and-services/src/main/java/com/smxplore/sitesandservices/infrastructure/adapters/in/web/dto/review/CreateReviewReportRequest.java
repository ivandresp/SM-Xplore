package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewReportRequest(
        @NotNull ReviewReportReason reason,
        @Size(max = ReviewReport.MAX_DETAILS_LENGTH) String details
) {
}
