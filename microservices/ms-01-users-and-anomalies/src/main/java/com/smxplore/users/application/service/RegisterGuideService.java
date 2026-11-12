package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RegisterGuideCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.RegisterGuideUseCase;
import com.smxplore.users.application.port.out.GuideRegistrationReceivedNotifier;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Registro público de guías turísticos.
 *
 * <p>El alta nace en {@code PENDIENTE}: el guía no puede iniciar sesión hasta que una gestora la apruebe. No se emite
 * token de verificación de correo porque la aprobación de una gestora es una comprobación humana más fuerte y el aviso
 * de la decisión viaja justamente a ese correo.
 */
public final class RegisterGuideService implements RegisterGuideUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final GuideRegistrationReceivedNotifier notifier;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterGuideService(
            UserRepository users,
            PasswordHasher passwordHasher,
            GuideRegistrationReceivedNotifier notifier,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.notifier = notifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<UserView> register(RegisterGuideCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());
        Instant now = clock.instant();

        return transactions
                .execute(Mono.defer(() -> users.existsByEmail(email).flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new EmailAlreadyExistsException());
                    }
                    return passwordHasher
                            .hash(command.rawPassword())
                            .map(passwordHash -> Guia.requestRegistration(
                                    UUID.randomUUID(),
                                    command.fullName(),
                                    email,
                                    command.phone(),
                                    passwordHash,
                                    command.dniType(),
                                    command.dniNumber(),
                                    command.license(),
                                    command.startOperationDate(),
                                    now))
                            .flatMap(users::save);
                })))
                .flatMap(this::notifyReceived)
                .map(UserView::from);
    }

    private Mono<Usuario> notifyReceived(Usuario guide) {
        return notifier.notify(guide).thenReturn(guide);
    }
}
