package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.ForgotPasswordCommand;
import com.smxplore.users.identity.application.model.PasswordResetPolicy;
import com.smxplore.users.identity.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.identity.application.port.out.PasswordResetNotifier;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TokenGenerator;
import com.smxplore.users.identity.application.port.out.TokenHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.TokenPurpose;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public final class ForgotPasswordService implements ForgotPasswordUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordResetNotifier notifier;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final PasswordResetPolicy policy;
    private final TransactionPort transactions;
    private final Clock clock;

    public ForgotPasswordService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordResetNotifier notifier,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            PasswordResetPolicy policy,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.notifier = notifier;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void handle(ForgotPasswordCommand command) {
        Optional<Usuario> user = users.findByEmail(new Email(command.email()));
        if (user.isEmpty() || !user.get().isActive()) {
            return;
        }

        Instant now = clock.instant();
        String rawToken = tokenGenerator.generate();
        PasswordResetToken token = PasswordResetToken.create(
                user.get().id(), tokenHasher.hash(rawToken), now, policy.ttl(), TokenPurpose.PASSWORD_RESET);

        transactions.execute(() -> {
            resetTokens.invalidateAllForUser(user.get().id(), now);
            resetTokens.save(token);
            return null;
        });

        notifier.notify(user.get(), rawToken);
    }
}
