package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.BootstrapAdminCommand;
import com.smxplore.users.identity.application.model.PasswordPolicy;
import com.smxplore.users.identity.application.port.in.BootstrapAdminUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.model.Admin;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Clock;

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
    public void bootstrap(BootstrapAdminCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        transactions.execute(() -> {
            if (users.existsByEmail(email)) {
                return null;
            }
            Admin admin = Admin.create(
                    UserId.newId(),
                    command.fullName(),
                    email,
                    null,
                    passwordHasher.hash(command.rawPassword()),
                    null,
                    null,
                    clock.instant());
            users.save(admin);
            return null;
        });
    }
}
