package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone;

import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;

import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
        UUID zoneId,
        UUID managerId,
        String name,
        String type,
        String description,
        ZoneStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
