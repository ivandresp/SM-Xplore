package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.RegisterProviderCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.model.PasswordPolicy;
import com.smxplore.users.identity.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Clock;

public final class RegisterProviderService implements RegisterProviderUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterProviderService(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public UserView register(RegisterProviderCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        return transactions.execute(() -> {
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyExistsException();
            }

            User user = User.registerProvider(
                    UserId.newId(),
                    command.fullName(),
                    email,
                    command.phone(),
                    passwordHasher.hash(command.rawPassword()),
                    command.nit(),
                    command.businessType(),
                    command.latitude(),
                    command.longitude(),
                    command.startOperationDate(),
                    clock.instant());

            return UserView.from(users.save(user));
        });
    }
}
