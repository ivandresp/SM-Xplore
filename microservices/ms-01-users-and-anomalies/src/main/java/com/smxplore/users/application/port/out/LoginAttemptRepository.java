package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public interface LoginAttemptRepository {

    /**
     * Tiempo restante de bloqueo para el correo indicado; cero si no está bloqueado.
     */
    Mono<Duration> lockRemaining(Email email, Instant now);

    /**
     * Registra un intento fallido y devuelve el tiempo de bloqueo resultante (cero si aún no se alcanzó el umbral).
     */
    Mono<Duration> recordFailure(Email email, Instant now, LoginLockoutPolicy policy);

    Mono<Void> clear(Email email);
}
