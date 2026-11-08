package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.BootstrapAdminCommand;
import com.smxplore.users.application.port.in.BootstrapAdminUseCase;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Crea el administrador inicial en el primer arranque de una instalación vacía.
 *
 * <p>MS01-12: antes bastaba con que el correo configurado no existiera para volver a crearlo en cada reinicio, así que
 * un administrador eliminado o degradado resucitaba con la contraseña de la variable de entorno. Ahora sólo se crea
 * cuando la tabla {@code users} está vacía y la creación queda auditada.
 */
public final class BootstrapAdminService implements BootstrapAdminUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final Clock clock;

    public BootstrapAdminService(
            UserRepository users,
            PasswordHasher passwordHasher,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Mono<Void> bootstrap(BootstrapAdminCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());

        return transactions
                .execute(Mono.defer(() -> users.countAll().flatMap(total -> {
                    if (total > 0) {
                        LOGGER.info("Bootstrap de administrador omitido: la instalación ya tiene cuentas registradas");
                        return Mono.empty();
                    }
                    return users.existsByEmail(email).flatMap(exists -> {
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
                                .flatMap(created -> audit.record(
                                                created.id(),
                                                AuditAction.ADMIN_INICIAL_CREADO,
                                                created.id(),
                                                Map.of("correo", created.email().value()),
                                                clock.instant())
                                        .thenReturn(created))
                                .then();
                    });
                })))
                .then();
    }
}
