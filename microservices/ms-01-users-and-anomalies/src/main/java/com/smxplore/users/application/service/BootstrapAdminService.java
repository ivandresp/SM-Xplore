package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.BootstrapAdminCommand;
import com.smxplore.users.application.port.in.BootstrapAdminUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class BootstrapAdminService implements BootstrapAdminUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionPort transactions;
    private final Clock clock;

    public BootstrapAdminService(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> bootstrap(BootstrapAdminCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        return transactions
                .execute(Mono.defer(() -> users.existsByEmail(email).flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    return passwordHasher
                            .hash(command.rawPassword())
                            .map(passwordHash -> Admin.create(
                                    UUID.randomUUID(),
                                    command.fullName(),
                                    email,
                                    null,
                                    passwordHash,
                                    null,
                                    null,
                                    clock.instant()))
                            .flatMap(users::save)
                            .then();
                })))
                .then();
    }
}
