package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ReviewGuideUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Aprueba o rechaza el registro de una guía. La ejecuta una gestora de destinos o un administrador (controlado en el
 * controlador con {@code @PreAuthorize}); cada decisión incrementa la versión de autenticación del guía, deja rastro de
 * auditoría y envía un correo.
 */
public final class ReviewGuideService implements ReviewGuideUseCase {

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final RegistrationReviewNotifier notifier;
    private final AuthVersionEvictor evictor;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReviewGuideService(
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
    public Mono<Void> review(UUID actorId, UUID guideId, ReviewRegistrationCommand command) {
        RegistrationStatus decision = requireDecision(command.decision());

        return transactions
                .execute(Mono.defer(() -> users.findById(guideId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> reviewTarget(actorId, target, decision, command))))
                .flatMap(guide -> notifier.notify(guide, decision, command.reason()));
    }

    private Mono<Guia> reviewTarget(
            UUID actorId, Usuario target, RegistrationStatus decision, ReviewRegistrationCommand command) {
        if (!(target instanceof Guia guide)) {
            return Mono.error(new ValidationException("Solo las guías pueden revisarse"));
        }
        if (guide.registrationStatus() == decision) {
            return Mono.error(
                    new InvalidStatusTransitionException("La guía ya se encuentra en estado " + decision.name()));
        }

        Instant now = clock.instant();
        guide.changeRegistrationStatus(decision, now);
        return users.save(guide)
                .then(audit.record(actorId, actionFor(decision), guide.id(), details(decision, command), now))
                .then(evictor.evict(guide.id()))
                .thenReturn(guide);
    }

    static RegistrationStatus requireDecision(RegistrationStatus decision) {
        if (decision == null) {
            throw new ValidationException("La decisión es obligatoria");
        }
        if (decision == RegistrationStatus.PENDIENTE) {
            throw new ValidationException("La decisión debe ser APROBADO o RECHAZADO");
        }
        return decision;
    }

    private static AuditAction actionFor(RegistrationStatus decision) {
        return decision == RegistrationStatus.APROBADO ? AuditAction.GUIA_APROBADA : AuditAction.GUIA_RECHAZADA;
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
