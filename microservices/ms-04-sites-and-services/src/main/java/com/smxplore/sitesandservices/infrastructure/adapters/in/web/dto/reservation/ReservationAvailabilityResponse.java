package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationAvailabilityResponse(
        UUID serviceId,
        String serviceName,
        UUID providerId,
        Instant bookedFor,
        LocalDate serviceDate,
        int maxCapacity,
        int reservedPeople,
        int remainingCapacity,
        int requestedPeople,
        boolean available,
        String reason
) {
}
