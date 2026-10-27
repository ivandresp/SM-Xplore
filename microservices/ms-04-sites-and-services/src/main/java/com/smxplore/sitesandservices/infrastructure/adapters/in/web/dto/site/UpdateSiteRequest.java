package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site;

import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateSiteRequest(
        @NotBlank String name,
        String description,
        @NotNull UUID categoryId,
        @NotNull UUID zoneId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        List<String> photosUrls,
        @Min(1) int maxCapacity,
        @Valid List<AvailabilitySlotRequest> availability
) {
}
