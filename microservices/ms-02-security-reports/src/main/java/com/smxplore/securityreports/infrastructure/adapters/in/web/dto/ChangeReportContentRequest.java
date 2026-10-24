package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeReportContentRequest(
        @NotBlank String title,
        @NotBlank String description
) {
}
