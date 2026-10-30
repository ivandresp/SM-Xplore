package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReviewReport(UUID reporterId, String reason, Instant reportedAt) {

    public ReviewReport {
        Objects.requireNonNull(reporterId, "El usuario que reporta es obligatorio");
        Objects.requireNonNull(reportedAt, "La fecha del reporte es obligatoria");
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("El motivo del reporte es obligatorio");
        }
        reason = reason.trim();
        if (reason.length() > 500) {
            throw new ValidationException("El motivo del reporte no puede superar los 500 caracteres");
        }
    }
}
