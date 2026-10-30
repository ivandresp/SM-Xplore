package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(@NotBlank @Size(max = 500) String reason) {}
