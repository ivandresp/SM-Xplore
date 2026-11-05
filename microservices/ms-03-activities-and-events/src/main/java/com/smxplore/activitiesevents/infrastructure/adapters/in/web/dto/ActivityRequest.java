package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ActivityRequest(
        @NotNull UUID siteId,
        UUID guideId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String category,
        @NotNull @DecimalMin(value = "0.0") BigDecimal price,
        @NotNull @Positive Integer maxCapacity,
        @NotNull @Positive Integer durationMinutes,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        String description
) {
}
