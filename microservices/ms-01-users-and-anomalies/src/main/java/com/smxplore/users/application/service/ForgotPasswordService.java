package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ForgotPasswordCommand;
import com.smxplore.users.application.port.in.ForgotPasswordUseCase;
import com.smxplore.users.application.port.out.PasswordResetNotifier;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordResetPolicy;
import java.time.Clock;
import java.time.Instant;
import reactor.core.publisher.Mono;

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
    public Mono<Void> handle(ForgotPasswordCommand command) {
        return users.findByEmail(new Email(command.email()))
                .filter(Usuario::isActive)
                .flatMap(user -> {
                    Instant now = clock.instant();
                    String rawToken = tokenGenerator.generate();
                    PasswordResetToken token = PasswordResetToken.create(
                            user.id(), tokenHasher.hash(rawToken), now, policy.ttl(), TokenPurpose.PASSWORD_RESET);

                    return transactions
                            .execute(resetTokens
                                    .invalidateAllForUser(user.id(), now)
                                    .then(resetTokens.save(token))
                                    .then())
                            .then(notifier.notify(user, rawToken));
                })
                .then();
    }
}
