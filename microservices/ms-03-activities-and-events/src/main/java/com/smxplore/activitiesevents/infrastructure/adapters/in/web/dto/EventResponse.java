package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.event.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID siteId,
        String name,
        String category,
        EventStatus status,
        BigDecimal price,
        Integer maxCapacity,
        Instant startsAt,
        Instant endsAt,
        String description,
        int expectedAttendance
) {
}
