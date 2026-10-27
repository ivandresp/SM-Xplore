package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site;

import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotResponse;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SiteResponse(
        UUID siteId,
        UUID managerId,
        String name,
        String description,
        UUID categoryId,
        String categoryName,
        UUID zoneId,
        String zoneName,
        ZoneStatus zoneStatus,
        double latitude,
        double longitude,
        List<String> photosUrls,
        ContentStatus status,
        int maxCapacity,
        List<AvailabilitySlotResponse> availability,
        Instant createdAt,
        Instant updatedAt
) {
}
