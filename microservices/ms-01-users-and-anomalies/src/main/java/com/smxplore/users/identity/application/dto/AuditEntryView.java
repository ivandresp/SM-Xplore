package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.AuditEntry;
import com.smxplore.users.identity.domain.model.Role;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryView(
        UUID id,
        UUID actorId,
        String actorEmail,
        Role actorRole,
        AuditAction action,
        UUID targetUserId,
        Map<String, String> details,
        Instant occurredAt) {

    public static AuditEntryView from(AuditEntry entry) {
        return new AuditEntryView(
                entry.id(),
                entry.actorId().value(),
                entry.actorEmail(),
                entry.actorRole(),
                entry.action(),
                entry.targetUserId() == null ? null : entry.targetUserId().value(),
                entry.details(),
                entry.occurredAt());
    }
}
