package com.smxplore.users.application.service;

import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import java.time.Clock;
import reactor.core.publisher.Mono;

/**
 * Protección contra fuerza bruta: bloquea temporalmente una cuenta tras varios intentos fallidos consecutivos.
 * La detección de anomalías sigue registrando los fallos por separado; este bloqueo evita que se sigan probando
 * contraseñas mientras dura la ventana.
 */
public final class LoginProtection {

    private final LoginAttemptRepository attempts;
    private final LoginLockoutPolicy policy;
    private final boolean enabled;
    private final Clock clock;

    public LoginProtection(LoginAttemptRepository attempts, LoginLockoutPolicy policy, boolean enabled, Clock clock) {
        this.attempts = attempts;
        this.policy = policy;
        this.enabled = enabled;
        this.clock = clock;
    }

    public Mono<Void> ensureNotLocked(Email email) {
        if (!enabled) {
            return Mono.empty();
        }
        return attempts.lockRemaining(email, clock.instant())
                .filter(remaining -> !remaining.isZero() && !remaining.isNegative())
                .flatMap(remaining -> Mono.<Void>error(new TooManyLoginAttemptsException(remaining)));
    }

    public Mono<Void> registerFailure(Email email) {
        if (!enabled) {
            return Mono.empty();
        }
        return attempts.recordFailure(email, clock.instant(), policy).then();
    }

    public Mono<Void> clear(Email email) {
        if (!enabled) {
            return Mono.empty();
        }
        return attempts.clear(email);
    }
}
