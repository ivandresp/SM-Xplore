package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerateReviewRequest(
        @NotBlank String decision, @Size(max = 500) String reason) {}
