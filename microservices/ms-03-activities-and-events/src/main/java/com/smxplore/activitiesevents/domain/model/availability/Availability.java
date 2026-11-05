package com.smxplore.activitiesevents.domain.model.availability;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;

import java.util.UUID;

public record Availability(
        UUID itemId,
        ReservationItemType itemType,
        int maxCapacity,
        int reserved,
        int available
) {
}
