package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.CreateInternalUserCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.CreateInternalUserUseCase;
import com.smxplore.users.application.port.out.AccountInvitationNotifier;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.AccountInvitationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class CreateInternalUserService implements CreateInternalUserUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final AccountInvitationNotifier notifier;
    private final AccountInvitationPolicy policy;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public CreateInternalUserService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordHasher passwordHasher,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            AccountInvitationNotifier notifier,
            AccountInvitationPolicy policy,
            AuditTrailRecorder audit,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.notifier = notifier;
        this.policy = policy;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<UserView> create(UUID actorId, CreateInternalUserCommand command) {
        Role role = requireInternalRole(command.role());
        Email email = new Email(command.email());

        // La cuenta nace con una contraseña aleatoria que nadie conoce; el acceso se obtiene con la invitación.
        return passwordHasher
                .hash(tokenGenerator.generate())
                .flatMap(unusablePassword -> transactions.execute(Mono.defer(() -> users.existsByEmail(email)
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new EmailAlreadyExistsException());
                            }
                            Instant now = clock.instant();
                            Usuario user = buildUser(role, command, email, unusablePassword, now);
                            String rawToken = tokenGenerator.generate();

                            return users.save(user)
                                    .then(tokens.save(PasswordResetToken.create(
                                            user.id(),
                                            tokenHasher.hash(rawToken),
                                            now,
                                            policy.ttl(),
                                            TokenPurpose.ACCOUNT_INVITATION)))
                                    .then(audit.record(
                                            actorId,
                                            AuditAction.CUENTA_INTERNA_CREADA,
                                            user.id(),
                                            Map.of("rol", role.code()),
                                            now))
                                    .thenReturn(new Invitation(user, rawToken));
                        }))))
                .flatMap(invitation -> notifier.notify(invitation.user(), invitation.rawToken())
                        .thenReturn(UserView.from(invitation.user())));
    }

    private static Usuario buildUser(
            Role role, CreateInternalUserCommand command, Email email, PasswordHash passwordHash, Instant now) {
        return switch (role) {
            case GUIDE ->
                Guia.register(
                        UUID.randomUUID(),
                        command.fullName(),
                        email,
                        command.phone(),
                        passwordHash,
                        requireDniType(command),
                        requireDniNumber(command),
                        requireLicense(command),
                        command.startOperationDate(),
                        now);
            case DESTINATION_MANAGER ->
                GestoraDestinos.register(
                        UUID.randomUUID(),
                        command.fullName(),
                        email,
                        command.phone(),
                        passwordHash,
                        requireNit(command),
                        command.startOperationDate(),
                        now);
            case ADMIN ->
                Admin.create(
                        UUID.randomUUID(),
                        command.fullName(),
                        email,
                        command.phone(),
                        passwordHash,
                        command.dniType(),
                        command.dniNumber(),
                        now);
            default ->
                throw new ValidationException(
                        "Solo se pueden crear cuentas de guía, gestora de destinos o administrador");
        };
    }

    private static Role requireInternalRole(Role role) {
        if (role == null) {
            throw new ValidationException("El rol es obligatorio");
        }
        return role;
    }

    private static String requireDniType(CreateInternalUserCommand command) {
        return requireText(command.dniType(), "El tipo de documento es obligatorio para un guía");
    }

    private static String requireDniNumber(CreateInternalUserCommand command) {
        return requireText(command.dniNumber(), "El número de documento es obligatorio para un guía");
    }

    private static String requireLicense(CreateInternalUserCommand command) {
        return requireText(command.license(), "La licencia es obligatoria para un guía");
    }

    private static String requireNit(CreateInternalUserCommand command) {
        return requireText(command.nit(), "El NIT es obligatorio para una gestora de destinos");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private record Invitation(Usuario user, String rawToken) {}
}
