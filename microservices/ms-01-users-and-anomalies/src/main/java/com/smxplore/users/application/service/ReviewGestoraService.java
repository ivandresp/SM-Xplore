package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.in.ReviewGestoraUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Aprueba o rechaza el registro de una gestora de destinos. Solo la ejecuta un administrador (controlado en el
 * controlador con {@code @PreAuthorize}).
 */
public final class ReviewGestoraService implements ReviewGestoraUseCase {

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final RegistrationReviewNotifier notifier;
    private final AuthVersionEvictor evictor;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReviewGestoraService(
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
    public Mono<Void> review(UUID actorId, UUID gestoraId, ReviewRegistrationCommand command) {
        RegistrationStatus decision = ReviewGuideService.requireDecision(command.decision());

        return transactions
                .execute(Mono.defer(() -> users.findById(gestoraId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> reviewTarget(actorId, target, decision, command))))
                .flatMap(gestora -> notifier.notify(gestora, decision, command.reason()));
    }

    private Mono<GestoraDestinos> reviewTarget(
            UUID actorId, Usuario target, RegistrationStatus decision, ReviewRegistrationCommand command) {
        if (!(target instanceof GestoraDestinos gestora)) {
            return Mono.error(new ValidationException("Solo las gestoras de destinos pueden revisarse"));
        }
        if (gestora.registrationStatus() == decision) {
            return Mono.error(new InvalidStatusTransitionException(
                    "La gestora de destinos ya se encuentra en estado " + decision.name()));
        }

        Instant now = clock.instant();
        gestora.changeRegistrationStatus(decision, now);
        return users.save(gestora)
                .then(audit.record(actorId, actionFor(decision), gestora.id(), details(decision, command), now))
                .then(evictor.evict(gestora.id()))
                .thenReturn(gestora);
    }

    private static AuditAction actionFor(RegistrationStatus decision) {
        return decision == RegistrationStatus.APROBADO ? AuditAction.GESTORA_APROBADA : AuditAction.GESTORA_RECHAZADA;
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
