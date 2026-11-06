package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(@NotBlank @Size(max = 500) String reason) {
}
