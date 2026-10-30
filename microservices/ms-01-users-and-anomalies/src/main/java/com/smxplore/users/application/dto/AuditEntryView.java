package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryView(
        UUID id,
        UUID actorId,
        String actorEmail,
        String actorRole,
        AuditAction action,
        UUID targetUserId,
        Map<String, String> details,
        Instant occurredAt) {

    public static AuditEntryView from(AuditEntry entry) {
        return new AuditEntryView(
                entry.id(),
                entry.actorId(),
                entry.actorEmail(),
                entry.actorRole().code(),
                entry.action(),
                entry.targetUserId(),
                entry.details(),
                entry.occurredAt());
    }
}
