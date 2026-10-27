package com.smxplore.sitesandservices.domain.model.common;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilitySlot(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt
) {

    public AvailabilitySlot {
        validateDayOfWeek(dayOfWeek);
        validateOpensAt(opensAt);
        validateClosesAt(closesAt);
        validateRange(opensAt, closesAt);
    }

    private static void validateDayOfWeek(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            throw new InvalidCatalogException("availability dayOfWeek is required");
        }
    }

    private static void validateOpensAt(LocalTime opensAt) {
        if (opensAt == null) {
            throw new InvalidCatalogException("availability opensAt is required");
        }
    }

    private static void validateClosesAt(LocalTime closesAt) {
        if (closesAt == null) {
            throw new InvalidCatalogException("availability closesAt is required");
        }
    }

    private static void validateRange(LocalTime opensAt, LocalTime closesAt) {
        if (!closesAt.isAfter(opensAt)) {
            throw new InvalidCatalogException("availability closesAt must be after opensAt");
        }
    }
}
