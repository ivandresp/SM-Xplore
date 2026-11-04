package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.DeleteUserUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.service.UserAnonymizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class DeleteUserService implements DeleteUserUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordHasher passwordHasher;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public DeleteUserService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            AuditTrailRecorder audit,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> delete(UUID actorId, UUID targetUserId) {
        // La contraseña se reemplaza por un hash BCrypt válido que nadie conoce.
        return passwordHasher
                .hash(UUID.randomUUID().toString())
                .flatMap(unusablePassword -> transactions.execute(Mono.defer(() -> users.findById(targetUserId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(target -> deleteAccount(actorId, target, unusablePassword)))))
                .then();
    }

    private Mono<Void> deleteAccount(UUID actorId, Usuario target, PasswordHash unusablePassword) {
        if (target.id().equals(actorId)) {
            return Mono.error(new ForbiddenOperationException("No puedes eliminar tu propia cuenta"));
        }
        if (target.status() == UserStatus.ELIMINADO) {
            return Mono.error(new InvalidStatusTransitionException("La cuenta ya está eliminada"));
        }

        boolean removesActiveAdmin = target.role() == Role.ADMIN && target.isActive();
        Mono<Long> activeAdmins = removesActiveAdmin ? users.countActiveByRole(Role.ADMIN) : Mono.just(Long.MAX_VALUE);

        return activeAdmins.flatMap(count -> {
            if (removesActiveAdmin && count <= 1) {
                return Mono.error(new ForbiddenOperationException("No puedes eliminar al último administrador activo"));
            }

            Instant now = clock.instant();
            Email anonymizedEmail = new Email("eliminado+" + target.id() + "@anonimo.smxplore.local");
            Usuario anonymized = UserAnonymizer.anonymize(target, anonymizedEmail, unusablePassword, now);

            return users.save(anonymized)
                    .then(resetTokens.invalidateAllForUser(target.id(), now))
                    .then(audit.record(actorId, AuditAction.CUENTA_ELIMINADA, target.id(), Map.of(), now));
        });
    }
}
