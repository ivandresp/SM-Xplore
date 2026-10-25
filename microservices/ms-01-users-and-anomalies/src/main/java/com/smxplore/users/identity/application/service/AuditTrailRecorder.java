package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.port.out.AuditLogRepository;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.ForbiddenOperationException;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.AuditEntry;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Instant;
import java.util.Map;

/**
 * Registra la trazabilidad de las acciones administrativas dentro de la misma transacción que la acción.
 */
public final class AuditTrailRecorder {

    private final UserRepository users;
    private final AuditLogRepository auditLog;

    public AuditTrailRecorder(UserRepository users, AuditLogRepository auditLog) {
        this.users = users;
        this.auditLog = auditLog;
    }

    public void record(
            UserId actorId, AuditAction action, UserId targetUserId, Map<String, String> details, Instant now) {
        Usuario actor = users.findById(actorId)
                .orElseThrow(() -> new ForbiddenOperationException("El usuario que ejecuta la acción no existe"));
        auditLog.save(
                AuditEntry.create(actor.id(), actor.email().value(), actor.role(), action, targetUserId, details, now));
    }
}
