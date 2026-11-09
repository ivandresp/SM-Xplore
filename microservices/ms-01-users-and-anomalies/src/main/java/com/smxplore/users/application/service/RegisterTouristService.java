package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.application.dto.RegisterTouristCommand;
import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.RegisterTouristUseCase;
import com.smxplore.users.application.port.out.EmailVerificationNotifier;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Registro de turistas (MS01-18: emite un token de verificación de correo, de un solo uso, dentro de la misma
 * transacción que crea la cuenta; el envío del correo ocurre después del commit y nunca hace fallar el registro).
 */
public final class RegisterTouristService implements RegisterTouristUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenRepository resetTokens;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final EmailVerificationNotifier notifier;
    private final EmailVerificationPolicy verification;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterTouristService(
            UserRepository users,
            PasswordHasher passwordHasher,
            PasswordResetTokenRepository resetTokens,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            EmailVerificationNotifier notifier,
            EmailVerificationPolicy verification,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.resetTokens = resetTokens;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.notifier = notifier;
        this.verification = verification;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<UserView> register(RegisterTouristCommand command) {
        PasswordPolicy.validate(command.rawPassword());
        Email email = new Email(command.email());
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        String rawVerificationToken = verification.enabled() ? tokenGenerator.generate() : null;

        return transactions
                .execute(Mono.defer(() -> users.existsByEmail(email).flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new EmailAlreadyExistsException());
                    }
                    return passwordHasher
                            .hash(command.rawPassword())
                            .map(passwordHash -> Turista.register(
                                    userId,
                                    command.fullName(),
                                    email,
                                    command.phone(),
                                    passwordHash,
                                    command.dniType(),
                                    command.dniNumber(),
                                    command.preferredLanguage(),
                                    command.birthDate(),
                                    command.nationality(),
                                    now))
                            .flatMap(users::save)
                            .flatMap(user -> saveVerificationToken(user, rawVerificationToken, now)
                                    .thenReturn(user));
                })))
                .flatMap(user -> notifyVerification(user, rawVerificationToken))
                .map(UserView::from);
    }

    private Mono<Void> saveVerificationToken(Usuario user, String rawToken, Instant now) {
        if (rawToken == null) {
            return Mono.empty();
        }
        PasswordResetToken token = PasswordResetToken.create(
                user.id(), tokenHasher.hash(rawToken), now, verification.tokenTtl(), TokenPurpose.EMAIL_VERIFICATION);
        return resetTokens
                .invalidateByPurposeForUser(user.id(), TokenPurpose.EMAIL_VERIFICATION, now)
                .then(resetTokens.save(token))
                .then();
    }

    private Mono<Usuario> notifyVerification(Usuario user, String rawToken) {
        if (rawToken == null) {
            return Mono.just(user);
        }
        return notifier.notify(user, rawToken).thenReturn(user);
    }
}
