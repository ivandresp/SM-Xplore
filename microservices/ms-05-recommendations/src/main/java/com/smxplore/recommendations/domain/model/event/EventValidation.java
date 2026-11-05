package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.exception.InvalidEventException;

import java.time.Instant;
import java.util.UUID;

public record EventValidation() {

    static void metadata(EventMetadata value) {
        if (value == null) {
            throw new InvalidEventException("event metadata is required");
        }
    }

    static void text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException(field + " is required");
        }
    }

    static void uuid(UUID value, String field) {
        if (value == null) {
            throw new InvalidEventException(field + " is required");
        }
    }

    static void instant(Instant value, String field) {
        if (value == null) {
            throw new InvalidEventException(field + " is required");
        }
    }

    static <T> void required(T value, String field) {
        if (value == null) {
            throw new InvalidEventException(field + " is required");
        }
    }

    static void latitude(double value) {
        if (!Double.isFinite(value) || value < -90.0 || value > 90.0) {
            throw new InvalidEventException("latitude must be between -90 and 90");
        }
    }

    static void longitude(double value) {
        if (!Double.isFinite(value) || value < -180.0 || value > 180.0) {
            throw new InvalidEventException("longitude must be between -180 and 180");
        }
    }

    static void nonNegative(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new InvalidEventException("price must be >= 0");
        }
    }

    static void rating(int value) {
        if (value < 1 || value > 5) {
            throw new InvalidEventException("rating must be between 1 and 5");
        }
    }
}
