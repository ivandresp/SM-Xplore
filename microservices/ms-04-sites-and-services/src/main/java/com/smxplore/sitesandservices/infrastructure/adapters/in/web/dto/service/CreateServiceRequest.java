package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service;

import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotRequest;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateServiceRequest(
        @NotBlank String name,
        String description,
        @NotBlank String type,
        @NotNull UUID categoryId,
        @NotNull UUID siteId,
        @NotNull UUID zoneId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(1) int maxCapacity,
        @Valid List<AvailabilitySlotRequest> availability,
        List<String> photosUrls,
        String cancellationPolicy,
        ContentStatus status
) {
}
