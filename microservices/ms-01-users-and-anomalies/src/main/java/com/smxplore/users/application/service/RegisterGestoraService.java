package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RegisterGestoraCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.RegisterGestoraUseCase;
import com.smxplore.users.application.port.out.GestoraRegistrationReceivedNotifier;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Registro público de gestoras de destinos.
 *
 * <p>El alta nace en {@code PENDIENTE}: la gestora no puede iniciar sesión hasta que un administrador la apruebe. No
 * se emite token de verificación de correo porque la aprobación de un administrador es una comprobación humana más
 * fuerte y el aviso de la decisión viaja justamente a ese correo.
 */
public final class RegisterGestoraService implements RegisterGestoraUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final GestoraRegistrationReceivedNotifier notifier;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterGestoraService(
            UserRepository users,
            PasswordHasher passwordHasher,
            GestoraRegistrationReceivedNotifier notifier,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.notifier = notifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<UserView> register(RegisterGestoraCommand command) {
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
                            .map(passwordHash -> GestoraDestinos.requestRegistration(
                                    UUID.randomUUID(),
                                    command.fullName(),
                                    email,
                                    command.phone(),
                                    passwordHash,
                                    command.nit(),
                                    command.startOperationDate(),
                                    now))
                            .flatMap(users::save);
                })))
                .flatMap(this::notifyReceived)
                .map(UserView::from);
    }

    private Mono<Usuario> notifyReceived(Usuario gestora) {
        return notifier.notify(gestora).thenReturn(gestora);
    }
}
