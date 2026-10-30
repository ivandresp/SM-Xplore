package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAnomalyThresholdRequest(
        @NotNull @Min(1) @Max(1_000_000) Integer thresholdValue,
        @NotNull @Min(10) @Max(3600) Integer windowSeconds,
        @NotBlank String severity,
        @NotNull Boolean enabled) {}
