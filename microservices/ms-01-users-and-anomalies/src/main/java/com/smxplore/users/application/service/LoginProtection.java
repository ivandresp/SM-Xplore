package com.smxplore.users.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.application.port.out.AccountLockedNotifier;
import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Protección contra fuerza bruta: bloquea temporalmente una cuenta tras varios intentos fallidos consecutivos.
 * La detección de anomalías sigue registrando los fallos por separado; este bloqueo evita que se sigan probando
 * contraseñas mientras dura la ventana.
 *
 * <p>MS01-05: el contador es compartido y persistente, de modo que un atacante puede mantener bloqueada la cuenta de
 * otra persona con intentos anónimos. Para que el bloqueo deje de ser silencioso, al activarse se avisa por correo al
 * titular (una sola vez por ventana) y la recuperación de contraseña limpia el bloqueo.
 */
public final class LoginProtection {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginProtection.class);

    private static final int NOTIFIED_LOCKS_MAX = 10_000;

    private final LoginAttemptRepository attempts;
    private final LoginLockoutPolicy policy;
    private final AccountLockedNotifier accountLockedNotifier;
    private final boolean enabled;
    private final Clock clock;
    private final Cache<Email, Boolean> notifiedLocks;

    public LoginProtection(
            LoginAttemptRepository attempts,
            LoginLockoutPolicy policy,
            AccountLockedNotifier accountLockedNotifier,
            boolean enabled,
            Clock clock) {
        this.attempts = attempts;
        this.policy = policy;
        this.accountLockedNotifier = accountLockedNotifier;
        this.enabled = enabled;
        this.clock = clock;
        this.notifiedLocks = Caffeine.newBuilder()
                .expireAfterWrite(policy.window())
                .maximumSize(NOTIFIED_LOCKS_MAX)
                .build();
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
        return attempts.recordFailure(email, clock.instant(), policy)
                .flatMap(lock -> notifyLockIfNeeded(email, lock).thenReturn(lock))
                .then();
    }

    public Mono<Void> clear(Email email) {
        if (!enabled) {
            return Mono.empty();
        }
        return attempts.clear(email);
    }

    private Mono<Void> notifyLockIfNeeded(Email email, Duration lock) {
        if (lock == null || lock.isZero() || lock.isNegative()) {
            return Mono.empty();
        }
        if (notifiedLocks.getIfPresent(email) != null) {
            return Mono.empty();
        }
        notifiedLocks.put(email, Boolean.TRUE);
        return accountLockedNotifier.notify(email, lock).onErrorResume(error -> {
            LOGGER.warn("No se pudo avisar del bloqueo de la cuenta {}", email.value(), error);
            return Mono.empty();
        });
    }
}
