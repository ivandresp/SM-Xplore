package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateActivityRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String category,
        String description,
        @NotNull @DecimalMin(value = "0.0") BigDecimal price
) {
}
