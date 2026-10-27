package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateReviewRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String comment) {}
