package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondReviewRequest(
        @NotBlank @Size(max = 1000) String text) {}
