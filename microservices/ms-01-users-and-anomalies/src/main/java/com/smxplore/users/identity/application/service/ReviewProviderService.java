package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.ReviewProviderCommand;
import com.smxplore.users.identity.application.port.in.ReviewProviderUseCase;
import com.smxplore.users.identity.application.port.out.ProviderReviewNotifier;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.exception.ValidationException;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public void review(UserId actorId, UserId targetUserId, ReviewProviderCommand command) {
        ProviderRegistrationStatus decision = requireDecision(command.decision());

        ReviewOutcome outcome = transactions.execute(() -> {
            Usuario target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
            if (!(target instanceof PrestadoraServicios provider)) {
                throw new ValidationException("Solo las prestadoras de servicios pueden revisarse");
            }
            if (provider.registrationStatus() == decision) {
                throw new InvalidStatusTransitionException(
                        "La prestadora ya se encuentra en estado " + decision.name());
            }

            Instant now = clock.instant();
            provider.changeRegistrationStatus(decision, now);
            users.save(provider);
            audit.record(actorId, actionFor(decision), provider.id(), details(decision, command), now);
            return new ReviewOutcome(provider, decision);
        });

        notifier.notify(outcome.provider(), outcome.decision(), command.reason());
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

    private record ReviewOutcome(Usuario provider, ProviderRegistrationStatus decision) {}
}
