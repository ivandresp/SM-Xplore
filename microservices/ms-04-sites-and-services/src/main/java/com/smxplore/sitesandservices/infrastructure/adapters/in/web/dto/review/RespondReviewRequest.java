package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import jakarta.validation.constraints.NotBlank;

public record RespondReviewRequest(
        @NotBlank String response
) {
}
