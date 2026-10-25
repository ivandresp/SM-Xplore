package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemResponse(
        UUID id,
        UUID reservationId,
        UUID itemId,
        ReservationItemType type,
        BigDecimal price
) {
}
