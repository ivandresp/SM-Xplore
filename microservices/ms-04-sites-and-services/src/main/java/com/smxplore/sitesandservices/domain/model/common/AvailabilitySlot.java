package com.smxplore.sitesandservices.domain.model.common;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Franja semanal de disponibilidad. {@code capacity} es el cupo del turno: {@code null}
 * significa que el turno no impone un límite propio y solo aplica la capacidad diaria
 * del sitio o servicio.
 */
public record AvailabilitySlot(
        DayOfWeek dayOfWeek,
        LocalTime opensAt,
        LocalTime closesAt,
        Integer capacity
) {

    public AvailabilitySlot(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this(dayOfWeek, opensAt, closesAt, null);
    }

    public AvailabilitySlot {
        validateDayOfWeek(dayOfWeek);
        validateOpensAt(opensAt);
        validateClosesAt(closesAt);
        validateRange(opensAt, closesAt);
        validateCapacity(capacity);
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

    private static void validateCapacity(Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw new InvalidCatalogException("availability capacity must be >= 1 when present");
        }
    }
}
