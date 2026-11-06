package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID touristId,
        String touristName,
        ReservationStatus status,
        BigDecimal total,
        int numberOfPeople,
        Instant bookedFor,
        String notes,
        List<ReservationItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason
) {
}
