package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;

import java.util.UUID;

public record AvailabilityResponse(
        UUID itemId,
        ReservationItemType itemType,
        int maxCapacity,
        int reserved,
        int available
) {
}
