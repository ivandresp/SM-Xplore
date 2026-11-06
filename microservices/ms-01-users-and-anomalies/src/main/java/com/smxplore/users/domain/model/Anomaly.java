package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.AnomalyAlreadyResolvedException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;

/**
 * Anomalía operativa o de seguridad detectada en la plataforma.
 *
 * <p>La anomalía nace en estado {@link AnomalyStatus#DETECTED}. El administrador puede reclasificarla por severidad y
 * resolverla dejando una nota de cierre. Una anomalía resuelta no se vuelve a resolver.
 */
public final class Anomaly {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_RESOLUTION_NOTE_LENGTH = 1000;

    private final UUID id;
    private final AnomalyType type;
    private final String description;
    private final Instant detectedAt;
    private AnomalySeverity severity;
    private AnomalyStatus status;
    private Instant resolvedAt;
    private UUID resolvedBy;
    private String resolutionNote;

    private Anomaly(
            UUID id,
            AnomalyType type,
            AnomalySeverity severity,
            AnomalyStatus status,
            String description,
            Instant detectedAt,
            Instant resolvedAt,
            UUID resolvedBy,
            String resolutionNote) {
        if (id == null) {
            throw new ValidationException("El identificador de la anomalía es obligatorio");
        }
        if (type == null) {
            throw new ValidationException("El tipo de anomalía es obligatorio");
        }
        if (severity == null) {
            throw new ValidationException("La severidad de la anomalía es obligatoria");
        }
        if (status == null) {
            throw new ValidationException("El estado de la anomalía es obligatorio");
        }
        if (detectedAt == null) {
            throw new ValidationException("La fecha de detección es obligatoria");
        }
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.status = status;
        this.description = normalizeDescription(description);
        this.detectedAt = detectedAt;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = normalizeNote(resolutionNote);
        validateResolution();
    }

    public static Anomaly detect(AnomalyType type, AnomalySeverity severity, String description, Instant detectedAt) {
        return new Anomaly(
                UUID.randomUUID(), type, severity, AnomalyStatus.DETECTED, description, detectedAt, null, null, null);
    }

    public static Anomaly rehydrate(
            UUID id,
            AnomalyType type,
            AnomalySeverity severity,
            AnomalyStatus status,
            String description,
            Instant detectedAt,
            Instant resolvedAt,
            UUID resolvedBy,
            String resolutionNote) {
        return new Anomaly(id, type, severity, status, description, detectedAt, resolvedAt, resolvedBy, resolutionNote);
    }

    public void classify(AnomalySeverity newSeverity) {
        if (newSeverity == null) {
            throw new ValidationException("La severidad de la anomalía es obligatoria");
        }
        this.severity = newSeverity;
    }

    public void resolve(UUID adminId, String note, Instant now) {
        if (status == AnomalyStatus.RESOLVED) {
            throw new AnomalyAlreadyResolvedException();
        }
        if (adminId == null) {
            throw new ValidationException("El administrador que resuelve la anomalía es obligatorio");
        }
        if (now == null) {
            throw new ValidationException("La fecha de resolución es obligatoria");
        }
        String normalizedNote = normalizeNote(note);
        this.status = AnomalyStatus.RESOLVED;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
        this.resolutionNote = normalizedNote;
    }

    private void validateResolution() {
        if (status == AnomalyStatus.RESOLVED) {
            if (resolvedAt == null || resolvedBy == null) {
                throw new ValidationException("Una anomalía resuelta requiere fecha y administrador de resolución");
            }
        } else if (resolvedAt != null || resolvedBy != null || resolutionNote != null) {
            throw new ValidationException("Una anomalía pendiente no puede tener datos de resolución");
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new ValidationException("La descripción de la anomalía es obligatoria");
        }
        String normalized = description.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException(
                    "La descripción de la anomalía no puede superar " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        return normalized;
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > MAX_RESOLUTION_NOTE_LENGTH) {
            throw new ValidationException(
                    "La nota de resolución no puede superar " + MAX_RESOLUTION_NOTE_LENGTH + " caracteres");
        }
        return normalized;
    }

    public UUID id() {
        return id;
    }

    public AnomalyType type() {
        return type;
    }

    public AnomalySeverity severity() {
        return severity;
    }

    public AnomalyStatus status() {
        return status;
    }

    public String description() {
        return description;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public UUID resolvedBy() {
        return resolvedBy;
    }

    public String resolutionNote() {
        return resolutionNote;
    }
}
