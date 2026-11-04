package com.smxplore.users.application.service;

import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

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

    public Mono<Void> record(
            UUID actorId, AuditAction action, UUID targetUserId, Map<String, String> details, Instant now) {
        return users.findById(actorId)
                .switchIfEmpty(
                        Mono.error(new ForbiddenOperationException("El usuario que ejecuta la acción no existe")))
                .flatMap(actor -> auditLog.save(AuditEntry.create(
                        actor.id(), actor.email().value(), actor.role(), action, targetUserId, details, now)))
                .then();
    }
}
