package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeReviewStatusRequest(
        @NotNull ReviewStatus status
) {
}
