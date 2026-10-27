package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.port.in.LoginUseCase;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.AccountNotActiveException;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class LoginService implements LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final FailedLoginSignal failedLogins;
    private final LoginProtection protection;
    private final MfaRepository mfa;
    private final MfaChallengeIssuer challengeIssuer;
    private final Clock clock;

    public LoginService(
            UserRepository users,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            FailedLoginSignal failedLogins,
            LoginProtection protection,
            MfaRepository mfa,
            MfaChallengeIssuer challengeIssuer,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.failedLogins = failedLogins;
        this.protection = protection;
        this.mfa = mfa;
        this.challengeIssuer = challengeIssuer;
        this.clock = clock;
    }

    @Override
    public Mono<LoginResult> login(LoginCommand command) {
        Email email = new Email(command.email());
        return protection
                .ensureNotLocked(email)
                .then(Mono.defer(() -> users.findByEmail(email)))
                .switchIfEmpty(Mono.defer(
                        () -> failedLogins.record().then(Mono.<Usuario>error(InvalidCredentialsException::new))))
                .flatMap(user -> passwordHasher
                        .matches(command.rawPassword(), user.passwordHash())
                        .flatMap(matches -> {
                            if (!matches) {
                                return protection
                                        .registerFailure(email)
                                        .then(failedLogins.record())
                                        .then(Mono.<Usuario>error(new InvalidCredentialsException()));
                            }
                            if (!user.isActive()) {
                                return protection
                                        .clear(email)
                                        .then(failedLogins.record())
                                        .then(Mono.<Usuario>error(new AccountNotActiveException()));
                            }
                            return protection.clear(email).thenReturn(user);
                        }))
                .flatMap(this::authenticated);
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
