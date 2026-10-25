package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.model.PasswordResetPolicy;
import com.smxplore.users.identity.application.port.in.AdminResetPasswordUseCase;
import com.smxplore.users.identity.application.port.out.PasswordResetNotifier;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TokenGenerator;
import com.smxplore.users.identity.application.port.out.TokenHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.TokenPurpose;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.UserStatus;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

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
    public void reset(UserId actorId, UserId targetUserId) {
        ResetOrder order = transactions.execute(() -> {
            Usuario user = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
            if (user.status() == UserStatus.ELIMINADO) {
                throw new InvalidStatusTransitionException("La cuenta está eliminada y no puede restablecerse");
            }

            Instant now = clock.instant();
            String rawToken = tokenGenerator.generate();
            resetTokens.invalidateAllForUser(user.id(), now);
            resetTokens.save(PasswordResetToken.create(
                    user.id(), tokenHasher.hash(rawToken), now, policy.ttl(), TokenPurpose.PASSWORD_RESET));
            audit.record(actorId, AuditAction.CONTRASENA_RESTABLECIDA, user.id(), Map.of(), now);
            return new ResetOrder(user, rawToken);
        });

        notifier.notify(order.user(), order.rawToken());
    }

    private record ResetOrder(Usuario user, String rawToken) {}
}
