package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service;

import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotResponse;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(
        UUID serviceId,
        UUID providerId,
        String providerName,
        UUID siteId,
        String siteName,
        UUID zoneId,
        String zoneName,
        ZoneStatus zoneStatus,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String type,
        double latitude,
        double longitude,
        BigDecimal price,
        ContentStatus status,
        int maxCapacity,
        List<AvailabilitySlotResponse> availability,
        List<String> photosUrls,
        String cancellationPolicy,
        Instant createdAt,
        Instant updatedAt
) {
}
