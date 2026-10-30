package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ChangePasswordCommand;
import com.smxplore.users.application.port.in.ChangeOwnPasswordUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

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
    public Mono<Void> change(UUID userId, ChangePasswordCommand command) {
        PasswordPolicy.validate(command.newPassword());

        return transactions
                .execute(Mono.defer(() -> users.findById(userId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(user -> matchesCurrentPassword(command, user).flatMap(matches -> {
                            if (!matches) {
                                return Mono.error(new InvalidCurrentPasswordException());
                            }
                            return changePassword(user, command);
                        }))))
                .then();
    }

    private Mono<Boolean> matchesCurrentPassword(ChangePasswordCommand command, Usuario user) {
        if (command.currentPassword() == null) {
            return Mono.just(false);
        }
        return passwordHasher.matches(command.currentPassword(), user.passwordHash());
    }

    private Mono<Void> changePassword(Usuario user, ChangePasswordCommand command) {
        Instant now = clock.instant();
        return passwordHasher.hash(command.newPassword()).flatMap(passwordHash -> {
            user.changePassword(passwordHash, now);
            return users.save(user).then(resetTokens.invalidateAllForUser(user.id(), now));
        });
    }
}
