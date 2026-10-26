package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ChangeUserRoleCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.ChangeUserRoleUseCase;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.RoleProfileData;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.service.RoleMigration;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ChangeUserRoleService implements ChangeUserRoleUseCase {

    private final UserRepository users;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public ChangeUserRoleService(
            UserRepository users, AuditTrailRecorder audit, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<UserView> change(UUID actorId, UUID targetUserId, ChangeUserRoleCommand command) {
        return transactions
                .execute(Mono.defer(() -> users.findById(targetUserId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> changeRole(actorId, target, command))))
                .map(UserView::from);
    }

    private Mono<Usuario> changeRole(UUID actorId, Usuario target, ChangeUserRoleCommand command) {
        if (target.id().equals(actorId)) {
            return Mono.error(new ForbiddenOperationException("No puedes cambiar el rol de tu propia cuenta"));
        }
        if (target.status() == UserStatus.ELIMINADO) {
            return Mono.error(
                    new InvalidStatusTransitionException("La cuenta está eliminada y no puede cambiar de rol"));
        }

        boolean removesActiveAdmin = target.role() == Role.ADMIN && target.isActive();
        Mono<Long> activeAdmins = removesActiveAdmin ? users.countActiveByRole(Role.ADMIN) : Mono.just(Long.MAX_VALUE);

        return activeAdmins.flatMap(count -> {
            if (removesActiveAdmin && count <= 1) {
                return Mono.error(
                        new ForbiddenOperationException("No puedes cambiar el rol del último administrador activo"));
            }

            Role newRole = command.role();
            Instant now = clock.instant();
            RoleProfileData profile = new RoleProfileData(
                    command.dniType(),
                    command.dniNumber(),
                    command.preferredLanguage(),
                    command.birthDate(),
                    command.nationality(),
                    command.license(),
                    command.nit(),
                    command.businessType(),
                    command.latitude(),
                    command.longitude(),
                    command.websiteUrl(),
                    command.startOperationDate());
            Usuario updated = RoleMigration.changeRole(target, newRole, profile, now);

            return users.deleteProfile(target.role(), target.id())
                    .then(users.save(updated))
                    .then(audit.record(
                            actorId, AuditAction.ROL_CAMBIADO, target.id(), details(target.role(), newRole), now))
                    .thenReturn(updated);
        });
    }

    private static Map<String, String> details(Role previousRole, Role newRole) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("rolAnterior", previousRole.code());
        details.put("rolNuevo", newRole.code());
        return details;
    }
}
