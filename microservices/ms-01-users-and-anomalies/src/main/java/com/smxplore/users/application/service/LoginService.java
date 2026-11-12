package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.out.AccountAttemptLimiter;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.AccountPendingReviewException;
import com.smxplore.users.domain.exception.AccountRegistrationRejectedException;
import com.smxplore.users.domain.exception.DomainException;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class LoginService implements LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final FailedLoginSignal failedLogins;
    private final LoginProtection protection;
    private final AccountAttemptLimiter accountAttemptLimiter;
    private final MfaRepository mfa;
    private final MfaChallengeIssuer challengeIssuer;
    private final Clock clock;
    /**
     * Hash señuelo para que un correo inexistente tarde lo mismo que una contraseña incorrecta y no se pueda enumerar
     * por tiempos de respuesta. Se calcula una sola vez al arrancar para que la primera consulta también tarde igual.
     */
    private final PasswordHash dummyPasswordHash;

    public LoginService(
            UserRepository users,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            FailedLoginSignal failedLogins,
            LoginProtection protection,
            AccountAttemptLimiter accountAttemptLimiter,
            MfaRepository mfa,
            MfaChallengeIssuer challengeIssuer,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.failedLogins = failedLogins;
        this.protection = protection;
        this.accountAttemptLimiter = accountAttemptLimiter;
        this.mfa = mfa;
        this.challengeIssuer = challengeIssuer;
        this.clock = clock;
        this.dummyPasswordHash =
                passwordHasher.hash(UUID.randomUUID().toString()).block();
    }

    @Override
    public Mono<LoginResult> login(LoginCommand command) {
        Email email = new Email(command.email());
        String accountKey = accountKey(email);
        return protection
                .ensureNotLocked(email)
                .then(accountAttemptLimiter.ensureAllowed(accountKey))
                .then(Mono.defer(() -> users.findByEmail(email)))
                .switchIfEmpty(Mono.defer(() -> passwordHasher
                        .matches(command.rawPassword(), dummyPasswordHash)
                        .then(accountAttemptLimiter.recordFailure(accountKey))
                        .then(failedLogins.record())
                        .then(Mono.<Usuario>error(InvalidCredentialsException::new))))
                .flatMap(user -> passwordHasher
                        .matches(command.rawPassword(), user.passwordHash())
                        .flatMap(matches -> {
                            if (!matches) {
                                return protection
                                        .registerFailure(email)
                                        .then(accountAttemptLimiter.recordFailure(accountKey))
                                        .then(failedLogins.record())
                                        .then(Mono.<Usuario>error(new InvalidCredentialsException()));
                            }
                            if (!user.isActive()) {
                                // MS01-04: una cuenta inactiva responde igual que una contraseña incorrecta.
                                // Antes se lanzaba AccountNotActiveException (403), lo que permitía distinguir
                                // cuentas existentes bloqueadas o suspendidas de correos inexistentes.
                                return protection
                                        .clear(email)
                                        .then(accountAttemptLimiter.clear(accountKey))
                                        .then(failedLogins.record())
                                        .then(Mono.<Usuario>error(new InvalidCredentialsException()));
                            }
                            if (user instanceof Guia guide
                                    && guide.registrationStatus() != RegistrationStatus.APROBADO) {
                                // MS01-B8: el alta de un guía no opera hasta que una gestora la apruebe. La rama se
                                // evalúa DESPUÉS de verificar la contraseña, así que no revela a un desconocido si
                                // el correo está registrado; y no cuenta como intento fallido, porque la
                                // contraseña era correcta.
                                return protection
                                        .clear(email)
                                        .then(accountAttemptLimiter.clear(accountKey))
                                        .then(Mono.<Usuario>error(reviewError(guide.registrationStatus())));
                            }
                            return protection
                                    .clear(email)
                                    .then(accountAttemptLimiter.clear(accountKey))
                                    .thenReturn(user);
                        }))
                .flatMap(this::authenticated);
    }

    private static DomainException reviewError(RegistrationStatus status) {
        return status == RegistrationStatus.RECHAZADO
                ? new AccountRegistrationRejectedException()
                : new AccountPendingReviewException();
    }

    private static String accountKey(Email email) {
        return "login:" + email.value();
    }

    private Mono<LoginResult> authenticated(Usuario user) {
        return mfa.findByUserId(user.id())
                .filter(MfaEnrollment::enabled)
                .flatMap(enrollment -> challengeIssuer.issue(user))
                .map(challenge -> LoginResult.mfaChallenge(
                        challenge.token(), secondsUntil(challenge.expiresAt()), challenge.expiresAt()))
                .switchIfEmpty(Mono.defer(() -> tokenIssuer
                        .issue(user, false)
                        .map(issued -> LoginResult.access(
                                issued.value(), secondsUntil(issued.expiresAt()), issued.expiresAt()))));
    }

    private long secondsUntil(Instant expiresAt) {
        return Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
    }
}
