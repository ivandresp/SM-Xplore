package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.ResetPasswordCommand;
import com.smxplore.users.identity.application.model.PasswordPolicy;
import com.smxplore.users.identity.application.port.in.ResetPasswordUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TokenHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.InvalidPasswordResetTokenException;
import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;

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
    public void reset(ResetPasswordCommand command) {
        PasswordPolicy.validate(command.newPassword());
        if (command.token() == null || command.token().isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }

        String tokenHash = tokenHasher.hash(command.token());
        Instant now = clock.instant();

        transactions.execute(() -> {
            PasswordResetToken token = resetTokens
                    .findByTokenHash(tokenHash)
                    .filter(candidate -> candidate.isUsable(now))
                    .orElseThrow(InvalidPasswordResetTokenException::new);

            Usuario user = users.findById(token.userId()).orElseThrow(InvalidPasswordResetTokenException::new);

            user.changePassword(passwordHasher.hash(command.newPassword()), now);
            users.save(user);
            resetTokens.save(token.markUsed(now));
            resetTokens.invalidateAllForUser(user.id(), now);
            return null;
        });
    }
}
