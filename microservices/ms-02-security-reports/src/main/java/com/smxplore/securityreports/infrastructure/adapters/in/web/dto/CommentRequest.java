package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @Valid UserRefDto user,
        @NotBlank String content
) {
}
