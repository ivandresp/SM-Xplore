package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.audit.SecurityAuditEntry;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SecurityAuditRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * MS04-09: cambiar el correo de contacto de un usuario redirige las notificaciones de sus
 * reservas, asi que exige confirmacion explicita del administrador y deja auditoria con el
 * valor anterior y el nuevo. Sin verificacion de titularidad por correo (fuera de alcance);
 * el riesgo residual queda documentado en el README.
 */
@RequiredArgsConstructor
public class NotificationContactService {

    private final NotificationContactRepositoryPort contactRepository;
    private final SecurityAuditRepositoryPort auditRepository;
    private final CatalogAccess access;
    private final Clock clock;

    public Mono<NotificationContact> upsert(
            UUID userId,
            String email,
            boolean confirmed,
            Actor actor
    ) {
        return access.requireAdmin(actor)
                .then(requireConfirmation(confirmed))
                .then(Mono.defer(() -> contactRepository.findByUserId(userId)))
                .flatMap(previous -> {
                    NotificationContact contact = NotificationContact.builder()
                            .userId(userId)
                            .email(email == null ? null : email.trim())
                            .updatedAt(Instant.now(clock))
                            .build();
                    contact.validate();
                    String oldEmail = previous.map(NotificationContact::email).orElse(null);
                    return contactRepository.upsert(contact)
                            .flatMap(saved -> audit(actor, "NOTIFICATION_CONTACT_UPSERT", userId,
                                    "oldEmail=" + oldEmail + ", newEmail=" + saved.email())
                                    .thenReturn(saved));
                });
    }

    public Mono<Void> delete(UUID userId, Actor actor) {
        return access.requireAdmin(actor)
                .then(Mono.defer(() -> contactRepository.findByUserId(userId)))
                .flatMap(previous -> {
                    String oldEmail = previous.map(NotificationContact::email).orElse(null);
                    return contactRepository.deleteByUserId(userId)
                            .then(audit(actor, "NOTIFICATION_CONTACT_DELETE", userId,
                                    "oldEmail=" + oldEmail));
                });
    }

    private Mono<Void> requireConfirmation(boolean confirmed) {
        return confirmed
                ? Mono.empty()
                : Mono.error(new InvalidCatalogException(
                        "Changing a notification contact requires explicit confirmation"));
    }

    private Mono<Void> audit(Actor actor, String action, UUID userId, String detail) {
        return auditRepository.append(new SecurityAuditEntry(
                UUID.randomUUID(),
                Instant.now(clock),
                action,
                actor.userId(),
                actor.role().name(),
                userId.toString(),
                detail));
    }
}
