package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de disponibilidad. Los campos de cupo ({@code maxCapacity}, {@code reservedPeople} y
 * {@code remainingCapacity}) llegan nulos a los turistas (MS04-08); solo el personal operativo
 * (proveedor dueño, gestora o ADMIN) ve el detalle.
 */
public record ReservationAvailabilityResponse(
        UUID serviceId,
        String serviceName,
        UUID providerId,
        Instant bookedFor,
        LocalDate serviceDate,
        Integer maxCapacity,
        Integer reservedPeople,
        Integer remainingCapacity,
        int requestedPeople,
        boolean available,
        String reason
) {
}
