package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;

/**
 * Configuración de la alerta automática de un tipo de anomalía.
 *
 * @param thresholdValue cantidad de eventos (o verificaciones fallidas) que dispara la anomalía
 * @param windowSeconds ventana de observación para los eventos; en disponibilidad cuenta como referencia temporal
 */
public record AnomalyThreshold(
        AnomalyType type,
        int thresholdValue,
        int windowSeconds,
        AnomalySeverity severity,
        boolean enabled,
        Instant updatedAt,
        UUID updatedBy) {

    public static final int MIN_WINDOW_SECONDS = 10;
    public static final int MAX_WINDOW_SECONDS = 3600;
    public static final int MAX_THRESHOLD_VALUE = 1_000_000;

    public AnomalyThreshold {
        if (type == null) {
            throw new ValidationException("El tipo de anomalía es obligatorio");
        }
        if (severity == null) {
            throw new ValidationException("La severidad de la anomalía es obligatoria");
        }
        if (updatedAt == null) {
            throw new ValidationException("La fecha de actualización del umbral es obligatoria");
        }
        if (thresholdValue < 1 || thresholdValue > MAX_THRESHOLD_VALUE) {
            throw new ValidationException("El umbral debe estar entre 1 y " + MAX_THRESHOLD_VALUE);
        }
        if (windowSeconds < MIN_WINDOW_SECONDS || windowSeconds > MAX_WINDOW_SECONDS) {
            throw new ValidationException("La ventana de observación debe estar entre " + MIN_WINDOW_SECONDS + " y "
                    + MAX_WINDOW_SECONDS + " segundos");
        }
    }

    public static AnomalyThreshold defaults(AnomalyType type, Instant now) {
        return new AnomalyThreshold(
                type,
                type.defaultThresholdValue(),
                type.defaultWindowSeconds(),
                type.defaultSeverity(),
                true,
                now,
                null);
    }

    public AnomalyThreshold update(
            int thresholdValue,
            int windowSeconds,
            AnomalySeverity newSeverity,
            boolean newEnabled,
            Instant now,
            UUID actorId) {
        return new AnomalyThreshold(type, thresholdValue, windowSeconds, newSeverity, newEnabled, now, actorId);
    }
}
