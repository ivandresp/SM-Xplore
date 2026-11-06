package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Listing view. It deliberately omits {@code notes}: free text can carry
 * sensitive data and a listing exposes every reservation at once. The full
 * reservation, notes included, stays on GET /reservations/{id}.
 */
public record ReservationSummaryResponse(
        UUID id,
        String code,
        UUID touristId,
        ReservationItemType itemType,
        UUID itemId,
        UUID providerId,
        UUID guideId,
        BigDecimal unitPrice,
        int numberOfPeople,
        BigDecimal total,
        PaymentMethod paymentMethod,
        ReservationStatus status,
        Instant bookedAt,
        Instant bookedFor,
        Instant itemStartsAt
) {
}
