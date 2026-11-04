package com.smxplore.sitesandservices.domain.model.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationAvailability(
        UUID serviceId,
        String serviceName,
        UUID providerId,
        Instant bookedFor,
        LocalDate serviceDate,
        int maxCapacity,
        int reservedPeople,
        int requestedPeople,
        boolean available,
        String reason
) {

    public int remainingCapacity() {
        return Math.max(0, maxCapacity - reservedPeople);
    }
}
