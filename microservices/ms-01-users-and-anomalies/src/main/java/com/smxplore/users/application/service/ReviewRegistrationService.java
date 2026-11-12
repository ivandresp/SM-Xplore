package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ReviewRegistrationUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.FieldValidationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.RegistrationNotFoundException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Revisión del alta de cualquier perfil revisable: guías y prestadoras de servicios comparten la misma máquina de
 * estados, la misma auditoría, la misma revocación de sesiones y el mismo aviso por correo.
 */
public final class ReviewRegistrationService implements ReviewRegistrationUseCase {

    private static final String NOT_REVIEWABLE = "Solo los guías y las prestadoras de servicios pueden revisarse";

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final RegistrationReviewNotifier notifier;
    private final AuthVersionEvictor evictor;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReviewRegistrationService(
            UserRepository users,
            AuditTrailRecorder audit,
            RegistrationReviewNotifier notifier,
            AuthVersionEvictor evictor,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
        this.evictor = evictor;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> review(UUID actorId, UUID targetUserId, ReviewRegistrationCommand command) {
        return review(actorId, targetUserId, command, false);
    }

    @Override
    public Mono<Void> reviewRegistration(UUID actorId, UUID registrationId, ReviewRegistrationCommand command) {
        return review(actorId, registrationId, command, true);
    }

    private Mono<Void> review(
            UUID actorId, UUID targetId, ReviewRegistrationCommand command, boolean registrationScope) {
        RegistrationStatus decision = requireDecision(command.decision());
        requireReasonForRejection(decision, command.reason());

        return transactions
                .execute(Mono.defer(() -> users.findById(targetId)
                        .switchIfEmpty(Mono.error(
                                registrationScope ? new RegistrationNotFoundException() : new UserNotFoundException()))
                        .flatMap(target -> applyDecision(actorId, target, decision, command, registrationScope))))
                .flatMap(target -> notifier.notify(target, decision, command.reason()));
    }

    private Mono<Usuario> applyDecision(
            UUID actorId,
            Usuario target,
            RegistrationStatus decision,
            ReviewRegistrationCommand command,
            boolean registrationScope) {
        if (!(target instanceof RegistrationReviewable reviewable)) {
            return Mono.error(
                    registrationScope ? new RegistrationNotFoundException() : new ValidationException(NOT_REVIEWABLE));
        }
        if (reviewable.registrationStatus() == decision) {
            return Mono.error(
                    new InvalidStatusTransitionException("El alta ya se encuentra en estado " + decision.name()));
        }

        Instant now = clock.instant();
        reviewable.changeRegistrationStatus(decision, now);
        return users.save(target)
                .then(audit.record(actorId, actionFor(target, decision), target.id(), details(decision, command), now))
                // MS01-15: aprobar o rechazar el alta incrementa authVersion del usuario.
                .then(evictor.evict(target.id()))
                .thenReturn(target);
    }

    private static RegistrationStatus requireDecision(RegistrationStatus decision) {
        if (decision == null) {
            throw new ValidationException("La decisión es obligatoria");
        }
        if (decision == RegistrationStatus.PENDIENTE) {
            throw new ValidationException("La decisión debe ser APROBADO o RECHAZADO");
        }
        return decision;
    }

    /**
     * Una baja sin motivo deja al titular sin saber qué corregir y contradice el flujo aprobado, así que el motivo es
     * obligatorio al rechazar (el correo de rechazo lo incluye).
     */
    private static void requireReasonForRejection(RegistrationStatus decision, String reason) {
        if (decision == RegistrationStatus.RECHAZADO && (reason == null || reason.isBlank())) {
            throw new FieldValidationException(
                    "El motivo es obligatorio al rechazar un alta",
                    Map.of("reason", "El motivo es obligatorio al rechazar un alta"));
        }
    }

    private static AuditAction actionFor(Usuario target, RegistrationStatus decision) {
        boolean guide = target instanceof Guia;
        return switch (decision) {
            case APROBADO -> guide ? AuditAction.GUIA_APROBADA : AuditAction.PRESTADORA_APROBADA;
            case RECHAZADO -> guide ? AuditAction.GUIA_RECHAZADA : AuditAction.PRESTADORA_RECHAZADA;
            case PENDIENTE -> throw new ValidationException("La decisión debe ser APROBADO o RECHAZADO");
        };
    }

    private static Map<String, String> details(RegistrationStatus decision, ReviewRegistrationCommand command) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("decision", decision.name());
        if (command.reason() != null && !command.reason().isBlank()) {
            details.put("motivo", command.reason().trim());
        }
        return details;
    }
}
