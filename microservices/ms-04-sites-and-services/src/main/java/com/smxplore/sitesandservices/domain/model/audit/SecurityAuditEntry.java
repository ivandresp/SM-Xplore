package com.smxplore.sitesandservices.domain.model.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoria de operaciones administrativas sensibles (MS04-09, MS04-10).
 */
public record SecurityAuditEntry(
        UUID auditId,
        Instant occurredAt,
        String action,
        UUID actorId,
        String actorRole,
        String targetId,
        String detail
) {
}
