package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondReviewRequest(@NotBlank @Size(max = 1000) String message) {
}
