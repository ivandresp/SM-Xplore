package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.application.port.in.ChangeUserStatusUseCase;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ChangeUserStatusService implements ChangeUserStatusUseCase {

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public ChangeUserStatusService(
            UserRepository users, AuditTrailRecorder audit, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> change(UUID actorId, UUID targetUserId, ChangeUserStatusCommand command) {
        UserStatus newStatus = requireOperationalStatus(command.status());

        return transactions
                .execute(Mono.defer(() -> users.findById(targetUserId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> changeStatus(actorId, target, newStatus, command))))
                .then();
    }

    private Mono<Usuario> changeStatus(
            UUID actorId, Usuario target, UserStatus newStatus, ChangeUserStatusCommand command) {
        if (target.id().equals(actorId)) {
            return Mono.error(new ForbiddenOperationException("No puedes cambiar el estado de tu propia cuenta"));
        }
        if (target.status() == UserStatus.ELIMINADO) {
            return Mono.error(new InvalidStatusTransitionException("La cuenta está eliminada y no puede reactivarse"));
        }
        if (target.status() == newStatus) {
            return Mono.error(
                    new InvalidStatusTransitionException("La cuenta ya se encuentra en estado " + newStatus.name()));
        }

        boolean wouldRemoveLastActiveAdmin =
                target.role() == Role.ADMIN && target.isActive() && newStatus != UserStatus.ACTIVO;
        Mono<Long> activeAdmins =
                wouldRemoveLastActiveAdmin ? users.countActiveByRole(Role.ADMIN) : Mono.just(Long.MAX_VALUE);

        return activeAdmins.flatMap(count -> {
            if (wouldRemoveLastActiveAdmin && count <= 1) {
                return Mono.error(new ForbiddenOperationException("No puedes bloquear al último administrador activo"));
            }

            Instant now = clock.instant();
            UserStatus previousStatus = target.status();
            target.changeStatus(newStatus, now);
            return users.save(target)
                    .then(audit.record(
                            actorId, AuditAction.ESTADO_CAMBIADO, target.id(), details(previousStatus, command), now))
                    .thenReturn(target);
        });
    }

    private static UserStatus requireOperationalStatus(UserStatus status) {
        if (status == null) {
            throw new ValidationException("El estado es obligatorio");
        }
        if (status == UserStatus.ELIMINADO) {
            throw new ValidationException(
                    "La eliminación de cuentas se gestiona con el flujo de baja, no con un cambio de estado");
        }
        return status;
    }

    private static Map<String, String> details(UserStatus previousStatus, ChangeUserStatusCommand command) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("estadoAnterior", previousStatus.name());
        details.put("estadoNuevo", command.status().name());
        if (command.reason() != null && !command.reason().isBlank()) {
            details.put("motivo", command.reason().trim());
        }
        return details;
    }
}
