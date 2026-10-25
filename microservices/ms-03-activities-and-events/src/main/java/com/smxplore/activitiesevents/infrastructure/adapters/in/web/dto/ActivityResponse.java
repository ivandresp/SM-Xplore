package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID siteId,
        UUID guideId,
        String name,
        String category,
        ActivityStatus status,
        BigDecimal price,
        Integer maxCapacity,
        Integer durationMinutes,
        Instant startsAt,
        Instant endsAt,
        String description
) {
}
