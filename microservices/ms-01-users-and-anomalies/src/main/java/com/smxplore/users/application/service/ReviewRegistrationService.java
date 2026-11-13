package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ReviewRegistrationUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.FieldValidationException;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.RegistrationNotFoundException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;
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

        return transactions
                .execute(Mono.defer(() -> users.findById(targetId)
                        .switchIfEmpty(Mono.error(
                                registrationScope ? new RegistrationNotFoundException() : new UserNotFoundException()))
                        .flatMap(target -> ensureActorMayReview(actorId, target, registrationScope)
                                .flatMap(reviewed ->
                                        applyDecision(actorId, reviewed, decision, command, registrationScope)))))
                .flatMap(target -> notifier.notify(target, decision, command.reason()));
    }

    /**
     * El alta de una gestora la revisa un administrador, no otra gestora: si no, cualquier gestora podría aprobar
     * gestoras y la cola de altas se convertiría en un mecanismo de escalada de privilegios.
     */
    private Mono<Usuario> ensureActorMayReview(UUID actorId, Usuario target, boolean registrationScope) {
        if (!(target instanceof GestoraDestinos)) {
            return Mono.just(target);
        }
        return users.findById(actorId)
                .switchIfEmpty(Mono.error(
                        registrationScope ? new RegistrationNotFoundException() : new UserNotFoundException()))
                .flatMap(actor -> actor.role() == Role.ADMIN
                        ? Mono.just(target)
                        : Mono.error(new ForbiddenOperationException(
                                "Solo un administrador puede revisar el alta de una gestora de destinos")));
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
        RegistrationStatus current = reviewable.registrationStatus();
        // La transición se comprueba antes que el motivo: un APROBADO que se intenta rechazar sin motivo es un 409
        // (estado terminal), no un 400 de validación (B8-14). La regla vive en el enum.
        if (!current.canTransitionTo(decision)) {
            return Mono.error(new InvalidStatusTransitionException(
                    current == RegistrationStatus.APROBADO
                            ? "El alta ya está aprobada; para revocarla cambia el estado del usuario desde administración"
                            : "El alta ya se encuentra en estado " + decision.name()));
        }
        requireReasonForRejection(decision, command.reason());
        // Reabrir un rechazo exige motivo: una lista cacheada no debe revertir un rechazo reciente sin querer
        // (B8-10).
        if (current == RegistrationStatus.RECHAZADO
                && decision == RegistrationStatus.APROBADO
                && (command.reason() == null || command.reason().isBlank())) {
            throw new FieldValidationException(
                    "El motivo es obligatorio al reabrir un alta rechazada",
                    Map.of("reason", "El motivo es obligatorio al reabrir un alta rechazada"));
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
        return switch (decision) {
            case APROBADO ->
                target instanceof Guia
                        ? AuditAction.GUIA_APROBADA
                        : target instanceof GestoraDestinos
                                ? AuditAction.GESTORA_APROBADA
                                : AuditAction.PRESTADORA_APROBADA;
            case RECHAZADO ->
                target instanceof Guia
                        ? AuditAction.GUIA_RECHAZADA
                        : target instanceof GestoraDestinos
                                ? AuditAction.GESTORA_RECHAZADA
                                : AuditAction.PRESTADORA_RECHAZADA;
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
