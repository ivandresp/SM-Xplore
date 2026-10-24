package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.RegisterTouristCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.model.PasswordPolicy;
import com.smxplore.users.identity.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.identity.domain.model.Dni;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.FullName;
import com.smxplore.users.identity.domain.model.Phone;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Clock;

public final class RegisterTouristService implements RegisterTouristUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterTouristService(
            UserRepository users, PasswordHasher passwordHasher, TransactionPort transactions, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public UserView register(RegisterTouristCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        return transactions.execute(() -> {
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyExistsException();
            }

            User user = User.registerTourist(
                    UserId.newId(),
                    new FullName(command.fullName()),
                    email,
                    new Phone(command.phone()),
                    passwordHasher.hash(command.rawPassword()),
                    new Dni(command.dniType(), command.dniNumber()),
                    command.preferredLanguage(),
                    command.birthDate(),
                    command.nationality(),
                    clock.instant());

            return UserView.from(users.save(user));
        });
    }
}
