package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.AdminResetPasswordUseCase;
import com.smxplore.users.application.port.out.PasswordResetNotifier;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordResetPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class AdminResetPasswordService implements AdminResetPasswordUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordResetNotifier notifier;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final PasswordResetPolicy policy;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public AdminResetPasswordService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordResetNotifier notifier,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            PasswordResetPolicy policy,
            AuditTrailRecorder audit,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.notifier = notifier;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.policy = policy;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> reset(UUID actorId, UUID targetUserId) {
        return transactions
                .execute(Mono.defer(() -> users.findById(targetUserId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(user -> {
                            if (user.status() == UserStatus.ELIMINADO) {
                                return Mono.error(new InvalidStatusTransitionException(
                                        "La cuenta está eliminada y no puede restablecerse"));
                            }
                            return issueResetToken(actorId, user);
                        })))
                .flatMap(order -> notifier.notify(order.user(), order.rawToken()));
    }

    private Mono<ResetOrder> issueResetToken(UUID actorId, Usuario user) {
        Instant now = clock.instant();
        String rawToken = tokenGenerator.generate();

        return resetTokens
                .invalidateAllForUser(user.id(), now)
                .then(resetTokens.save(PasswordResetToken.create(
                        user.id(), tokenHasher.hash(rawToken), now, policy.ttl(), TokenPurpose.PASSWORD_RESET)))
                .then(audit.record(actorId, AuditAction.CONTRASENA_RESTABLECIDA, user.id(), Map.of(), now))
                .thenReturn(new ResetOrder(user, rawToken));
    }

    private record ResetOrder(Usuario user, String rawToken) {}
}
