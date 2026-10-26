package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        UUID actorId,
        String actorEmail,
        Role actorRole,
        AuditAction action,
        UUID targetUserId,
        Map<String, String> details,
        Instant occurredAt) {

    public AuditEntry {
        Objects.requireNonNull(id, "El identificador de la auditoría es obligatorio");
        Objects.requireNonNull(actorId, "El actor de la auditoría es obligatorio");
        Objects.requireNonNull(actorRole, "El rol del actor es obligatorio");
        Objects.requireNonNull(action, "La acción auditada es obligatoria");
        Objects.requireNonNull(occurredAt, "La fecha de la auditoría es obligatoria");
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new ValidationException("El correo del actor es obligatorio");
        }
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditEntry create(
            UUID actorId,
            String actorEmail,
            Role actorRole,
            AuditAction action,
            UUID targetUserId,
            Map<String, String> details,
            Instant occurredAt) {
        return new AuditEntry(
                UUID.randomUUID(), actorId, actorEmail, actorRole, action, targetUserId, details, occurredAt);
    }

    public static AuditEntry rehydrate(
            UUID id,
            UUID actorId,
            String actorEmail,
            Role actorRole,
            AuditAction action,
            UUID targetUserId,
            Map<String, String> details,
            Instant occurredAt) {
        return new AuditEntry(id, actorId, actorEmail, actorRole, action, targetUserId, details, occurredAt);
    }
}
