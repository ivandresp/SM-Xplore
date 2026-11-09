package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.VerifyMfaCommand;
import com.smxplore.users.application.port.in.VerifyMfaLoginUseCase;
import com.smxplore.users.application.port.out.AccountAttemptLimiter;
import com.smxplore.users.application.port.out.MfaChallengeRepository;
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
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class VerifyMfaLoginService implements VerifyMfaLoginUseCase {

    private final UserRepository users;
    private final MfaRepository mfa;
    private final MfaChallengeVerifier challengeVerifier;
    private final MfaChallengeRepository challenges;
    private final MfaCodeVerifier codeVerifier;
    private final AccountAttemptLimiter accountAttemptLimiter;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public VerifyMfaLoginService(
            UserRepository users,
            MfaRepository mfa,
            MfaChallengeVerifier challengeVerifier,
            MfaChallengeRepository challenges,
            MfaCodeVerifier codeVerifier,
            AccountAttemptLimiter accountAttemptLimiter,
            TokenIssuer tokenIssuer,
            Clock clock) {
        this.users = users;
        this.mfa = mfa;
        this.challengeVerifier = challengeVerifier;
        this.challenges = challenges;
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
                .flatMap(challenge -> users.findById(challenge.userId())
                        .switchIfEmpty(Mono.error(InvalidMfaChallengeException::new))
                        .flatMap(user -> {
                            if (!user.isActive()) {
                                return Mono.error(new AccountNotActiveException());
                            }
                            // MS01-08: el desafío queda ligado a la versión de sesión en la que se emitió. Si el
                            // usuario cambió la contraseña, cerró sesión o fue bloqueado, el desafío deja de valer.
                            if (user.authVersion() != challenge.authVersion()) {
                                return Mono.error(new InvalidMfaChallengeException());
                            }
                            return verifyCode(user, command.code(), now)
                                    .flatMap(verified ->
                                            consume(challenge.jti(), now).thenReturn(verified));
                        }))
                .flatMap(user -> tokenIssuer
                        .issue(user, true)
                        .map(issued -> LoginResult.access(
                                issued.value(), secondsUntil(issued.expiresAt()), issued.expiresAt())));
    }

    private Mono<Usuario> verifyCode(Usuario user, String code, Instant now) {
        // MS01-06: además del límite por IP del filtro HTTP, se limita por cuenta para que un atacante con la
        // contraseña no pueda probar códigos TOTP desde muchas IPs.
        String accountKey = "mfa:" + user.id();
        return accountAttemptLimiter
                .ensureAllowed(accountKey)
                .then(Mono.defer(() -> mfa.findByUserId(user.id())
                        .filter(MfaEnrollment::enabled)
                        .switchIfEmpty(Mono.error(InvalidMfaCodeException::new))))
                .flatMap(enrollment -> codeVerifier
                        .verify(user.id(), enrollment, code, now)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.defer(() -> accountAttemptLimiter
                                .recordFailure(accountKey)
                                .then(Mono.error(InvalidMfaCodeException::new))))
                        .then(accountAttemptLimiter.clear(accountKey))
                        .thenReturn(user));
    }

    private Mono<Void> consume(UUID jti, Instant now) {
        // MS01-06: el desafío se canjea una sola vez. Si dos peticiones compiten, solo una obtiene el token.
        return challenges
                .consume(jti, now)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new InvalidMfaChallengeException()))
                .then();
    }

    private long secondsUntil(Instant expiresAt) {
        return Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
    }
}
