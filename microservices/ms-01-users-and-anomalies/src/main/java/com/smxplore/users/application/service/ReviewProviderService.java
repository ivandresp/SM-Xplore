package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReviewProviderCommand;
import com.smxplore.users.application.port.in.ReviewProviderUseCase;
import com.smxplore.users.application.port.out.ProviderReviewNotifier;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ReviewProviderService implements ReviewProviderUseCase {

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final ProviderReviewNotifier notifier;
    private final TransactionPort transactions;
    private final Clock clock;

    public ReviewProviderService(
            UserRepository users,
            AuditTrailRecorder audit,
            ProviderReviewNotifier notifier,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> review(UUID actorId, UUID targetUserId, ReviewProviderCommand command) {
        ProviderRegistrationStatus decision = requireDecision(command.decision());

        return transactions
                .execute(Mono.defer(() -> users.findById(targetUserId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> reviewTarget(actorId, target, decision, command))))
                .flatMap(provider -> notifier.notify(provider, decision, command.reason()));
    }

    private Mono<PrestadoraServicios> reviewTarget(
            UUID actorId, Usuario target, ProviderRegistrationStatus decision, ReviewProviderCommand command) {
        if (!(target instanceof PrestadoraServicios provider)) {
            return Mono.error(new ValidationException("Solo las prestadoras de servicios pueden revisarse"));
        }
        if (provider.registrationStatus() == decision) {
            return Mono.error(
                    new InvalidStatusTransitionException("La prestadora ya se encuentra en estado " + decision.name()));
        }

        Instant now = clock.instant();
        provider.changeRegistrationStatus(decision, now);
        return users.save(provider)
                .then(audit.record(actorId, actionFor(decision), provider.id(), details(decision, command), now))
                .thenReturn(provider);
    }

    private static ProviderRegistrationStatus requireDecision(ProviderRegistrationStatus decision) {
        if (decision == null) {
            throw new ValidationException("La decisión es obligatoria");
        }
        if (decision == ProviderRegistrationStatus.PENDIENTE) {
            throw new ValidationException("La decisión debe ser APROBADO o RECHAZADO");
        }
        return decision;
    }

    private static AuditAction actionFor(ProviderRegistrationStatus decision) {
        return decision == ProviderRegistrationStatus.APROBADO
                ? AuditAction.PRESTADORA_APROBADA
                : AuditAction.PRESTADORA_RECHAZADA;
    }

    private static Map<String, String> details(ProviderRegistrationStatus decision, ReviewProviderCommand command) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("decision", decision.name());
        if (command.reason() != null && !command.reason().isBlank()) {
            details.put("motivo", command.reason().trim());
        }
        return details;
    }
}
