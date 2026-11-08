package com.smxplore.users.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Limita los intentos fallidos por cuenta (correo en el inicio de sesión, usuario en la verificación MFA) para que un
 * atacante no pueda probar credenciales o códigos rotando direcciones IP.
 */
public interface AccountAttemptLimiter {

    /**
     * Falla con {@code TooManyLoginAttemptsException} si la cuenta agotó el cupo de intentos de la ventana.
     */
    Mono<Void> ensureAllowed(String key);

    Mono<Void> recordFailure(String key);

    Mono<Void> clear(String key);
}
