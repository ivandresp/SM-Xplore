package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeReportContentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String description
) {
}
