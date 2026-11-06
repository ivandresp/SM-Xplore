package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeReviewStatusRequest(
        @NotNull ReviewStatus status
) {
}
