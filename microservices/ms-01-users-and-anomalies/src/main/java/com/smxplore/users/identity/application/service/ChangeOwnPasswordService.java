package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.ChangePasswordCommand;
import com.smxplore.users.identity.application.model.PasswordPolicy;
import com.smxplore.users.identity.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;

public final class ChangeOwnPasswordService implements ChangeOwnPasswordUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordHasher passwordHasher;
    private final TransactionPort transactions;
    private final Clock clock;

    public ChangeOwnPasswordService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordHasher passwordHasher,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void change(UserId userId, ChangePasswordCommand command) {
        PasswordPolicy.validate(command.newPassword());

        transactions.execute(() -> {
            Usuario user = users.findById(userId).orElseThrow(UserNotFoundException::new);
            if (command.currentPassword() == null
                    || !passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
                throw new InvalidCurrentPasswordException();
            }

            Instant now = clock.instant();
            user.changePassword(passwordHasher.hash(command.newPassword()), now);
            users.save(user);
            resetTokens.invalidateAllForUser(user.id(), now);
            return null;
        });
    }
}
