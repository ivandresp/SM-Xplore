package com.smxplore.sitesandservices.domain.model.reservation;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.Instant;
import java.util.UUID;

public record ReservationFilter(
        UUID touristId,
        UUID providerId,
        UUID serviceId,
        ReservationStatus status,
        Instant from,
        Instant to,
        int page,
        int size
) {

    public ReservationFilter {
        validatePage(page);
        validateSize(size);
        validateRange(from, to);
    }

    private static void validatePage(int page) {
        if (page < 0) {
            throw new InvalidCatalogException("page must be >= 0");
        }
    }

    private static void validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new InvalidCatalogException("size must be between 1 and 100");
        }
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidCatalogException("to must be after from");
        }
    }
}
