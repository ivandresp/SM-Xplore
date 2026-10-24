package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @NotBlank String content
) {
}
