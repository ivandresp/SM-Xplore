package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.CreateInternalUserCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.model.AccountInvitationPolicy;
import com.smxplore.users.identity.application.port.in.CreateInternalUserUseCase;
import com.smxplore.users.identity.application.port.out.AccountInvitationNotifier;
import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.application.port.out.TokenGenerator;
import com.smxplore.users.identity.application.port.out.TokenHasher;
import com.smxplore.users.identity.application.port.out.TransactionPort;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.identity.domain.exception.ValidationException;
import com.smxplore.users.identity.domain.model.Admin;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.Email;
import com.smxplore.users.identity.domain.model.GestoraDestinos;
import com.smxplore.users.identity.domain.model.Guia;
import com.smxplore.users.identity.domain.model.PasswordHash;
import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.TokenPurpose;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

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
    public UserView create(UserId actorId, CreateInternalUserCommand command) {
        Role role = requireInternalRole(command.role());
        Email email = new Email(command.email());
        // La cuenta nace con una contraseña aleatoria que nadie conoce; el acceso se obtiene con la invitación.
        PasswordHash unusablePassword = passwordHasher.hash(tokenGenerator.generate());

        Invitation invitation = transactions.execute(() -> {
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyExistsException();
            }

            Instant now = clock.instant();
            Usuario user = buildUser(role, command, email, unusablePassword, now);
            users.save(user);

            String rawToken = tokenGenerator.generate();
            tokens.save(PasswordResetToken.create(
                    user.id(), tokenHasher.hash(rawToken), now, policy.ttl(), TokenPurpose.ACCOUNT_INVITATION));
            audit.record(actorId, AuditAction.CUENTA_INTERNA_CREADA, user.id(), Map.of("rol", role.name()), now);
            return new Invitation(user, rawToken);
        });

        notifier.notify(invitation.user(), invitation.rawToken());
        return UserView.from(invitation.user());
    }

    private static Usuario buildUser(
            Role role, CreateInternalUserCommand command, Email email, PasswordHash passwordHash, Instant now) {
        return switch (role) {
            case GUIA ->
                Guia.register(
                        UserId.newId(),
                        command.fullName(),
                        email,
                        command.phone(),
                        passwordHash,
                        requireDniType(command),
                        requireDniNumber(command),
                        requireLicense(command),
                        command.startOperationDate(),
                        now);
            case GESTORA_DESTINOS ->
                GestoraDestinos.register(
                        UserId.newId(),
                        command.fullName(),
                        email,
                        command.phone(),
                        passwordHash,
                        requireNit(command),
                        command.startOperationDate(),
                        now);
            case ADMIN ->
                Admin.create(
                        UserId.newId(),
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
