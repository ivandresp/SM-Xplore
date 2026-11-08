package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.VerifyMfaCommand;
import com.smxplore.users.application.port.in.VerifyMfaLoginUseCase;
import com.smxplore.users.application.port.out.AccountAttemptLimiter;
import com.smxplore.users.application.port.out.MfaChallengeVerifier;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.AccountNotActiveException;
import com.smxplore.users.domain.exception.InvalidMfaChallengeException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class VerifyMfaLoginService implements VerifyMfaLoginUseCase {

    private final UserRepository users;
    private final MfaRepository mfa;
    private final MfaChallengeVerifier challengeVerifier;
    private final MfaCodeVerifier codeVerifier;
    private final AccountAttemptLimiter accountAttemptLimiter;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public VerifyMfaLoginService(
            UserRepository users,
            MfaRepository mfa,
            MfaChallengeVerifier challengeVerifier,
            MfaCodeVerifier codeVerifier,
            AccountAttemptLimiter accountAttemptLimiter,
            TokenIssuer tokenIssuer,
            Clock clock) {
        this.users = users;
        this.mfa = mfa;
        this.challengeVerifier = challengeVerifier;
        this.codeVerifier = codeVerifier;
        this.accountAttemptLimiter = accountAttemptLimiter;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    public Mono<LoginResult> verify(VerifyMfaCommand command) {
        Instant now = clock.instant();
        return challengeVerifier
                .verify(command.challengeToken())
                .onErrorMap(
                        error -> !(error instanceof InvalidMfaChallengeException),
                        error -> new InvalidMfaChallengeException())
                .flatMap(users::findById)
                .switchIfEmpty(Mono.error(InvalidMfaChallengeException::new))
                .flatMap(user -> {
                    if (!user.isActive()) {
                        return Mono.error(new AccountNotActiveException());
                    }
                    return verifyCode(user, command.code(), now);
                })
                .flatMap(user -> tokenIssuer.issue(user, true))
                .map(issuedToken -> LoginResult.access(
                        issuedToken.value(),
                        secondsUntil(issuedToken.expiresAt(), clock.instant()),
                        issuedToken.expiresAt()));
    }

    private Mono<Usuario> verifyCode(Usuario user, String code, Instant now) {
        String accountKey = accountKey(user);
        return accountAttemptLimiter
                .ensureAllowed(accountKey)
                .then(mfa.findByUserId(user.id()))
                .filter(MfaEnrollment::enabled)
                .switchIfEmpty(Mono.error(new InvalidMfaCodeException()))
                .flatMap(enrollment -> codeVerifier.verify(user.id(), enrollment, code, now))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(
                        accountAttemptLimiter.recordFailure(accountKey).then(Mono.error(new InvalidMfaCodeException())))
                .then(accountAttemptLimiter.clear(accountKey))
                .thenReturn(user);
    }

    private static String accountKey(Usuario user) {
        return "mfa:" + user.id();
    }

    static long secondsUntil(Instant expiresAt, Instant now) {
        return Math.max(0, Duration.between(now, expiresAt).toSeconds());
    }
}
