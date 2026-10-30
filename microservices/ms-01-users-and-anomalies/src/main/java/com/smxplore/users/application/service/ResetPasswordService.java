package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ResetPasswordCommand;
import com.smxplore.users.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidPasswordResetTokenException;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class ResetPasswordService implements ResetPasswordUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final TransactionPort transactions;
    private final Clock clock;

    public ResetPasswordService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            TokenHasher tokenHasher,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordHasher = passwordHasher;
        this.tokenHasher = tokenHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> reset(ResetPasswordCommand command) {
        PasswordPolicy.validate(command.newPassword());
        if (command.token() == null || command.token().isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }

        String tokenHash = tokenHasher.hash(command.token());
        Instant now = clock.instant();

        return transactions
                .execute(Mono.defer(() -> resetTokens
                        .findByTokenHash(tokenHash)
                        .filter(token -> token.isUsable(now))
                        .switchIfEmpty(Mono.error(InvalidPasswordResetTokenException::new))
                        .flatMap(token -> users.findById(token.userId())
                                .switchIfEmpty(Mono.error(InvalidPasswordResetTokenException::new))
                                .flatMap(user -> passwordHasher
                                        .hash(command.newPassword())
                                        .flatMap(passwordHash -> {
                                            user.changePassword(passwordHash, now);
                                            return users.save(user)
                                                    .then(resetTokens.save(token.markUsed(now)))
                                                    .then(resetTokens.invalidateAllForUser(user.id(), now));
                                        })))))
                .then();
    }
}
