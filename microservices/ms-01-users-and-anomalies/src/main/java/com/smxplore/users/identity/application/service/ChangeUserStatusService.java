package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.identity.application.port.in.ChangeUserStatusUseCase;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.ForbiddenOperationException;
import com.smxplore.users.identity.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.exception.ValidationException;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.UserStatus;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public void change(UserId actorId, UserId targetUserId, ChangeUserStatusCommand command) {
        UserStatus newStatus = requireOperationalStatus(command.status());

        transactions.execute(() -> {
            Usuario target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
            if (target.id().equals(actorId)) {
                throw new ForbiddenOperationException("No puedes cambiar el estado de tu propia cuenta");
            }
            if (target.status() == UserStatus.ELIMINADO) {
                throw new InvalidStatusTransitionException("La cuenta está eliminada y no puede reactivarse");
            }
            if (target.status() == newStatus) {
                throw new InvalidStatusTransitionException("La cuenta ya se encuentra en estado " + newStatus.name());
            }
            if (target.role() == Role.ADMIN
                    && target.isActive()
                    && newStatus != UserStatus.ACTIVO
                    && users.countActiveByRole(Role.ADMIN) <= 1) {
                throw new ForbiddenOperationException("No puedes bloquear al último administrador activo");
            }

            Instant now = clock.instant();
            UserStatus previousStatus = target.status();
            target.changeStatus(newStatus, now);
            users.save(target);
            audit.record(actorId, AuditAction.ESTADO_CAMBIADO, target.id(), details(previousStatus, command), now);
            return null;
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
