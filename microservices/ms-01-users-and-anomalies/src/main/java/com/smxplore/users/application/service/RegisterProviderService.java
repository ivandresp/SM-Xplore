package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RegisterProviderCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.RegisterProviderUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

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
    public Mono<UserView> register(RegisterProviderCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        return transactions.execute(Mono.defer(() -> users.existsByEmail(email).flatMap(exists -> {
            if (exists) {
                return Mono.error(new EmailAlreadyExistsException());
            }
            return passwordHasher
                    .hash(command.rawPassword())
                    .map(passwordHash -> PrestadoraServicios.register(
                            UUID.randomUUID(),
                            command.fullName(),
                            email,
                            command.phone(),
                            passwordHash,
                            command.nit(),
                            command.businessType(),
                            command.latitude(),
                            command.longitude(),
                            command.websiteUrl(),
                            command.startOperationDate(),
                            clock.instant()))
                    .flatMap(users::save)
                    .map(UserView::from);
        })));
    }
}
