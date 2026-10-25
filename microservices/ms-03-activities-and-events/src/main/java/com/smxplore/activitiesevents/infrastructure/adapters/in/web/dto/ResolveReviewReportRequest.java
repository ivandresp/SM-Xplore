package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveReviewReportRequest(@NotNull ReviewReportStatus status) {
}
