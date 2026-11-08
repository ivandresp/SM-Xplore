package com.smxplore.users.application.port.out;

import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Estado de verificación del correo electrónico (MS01-18).
 *
 * <p>La verificación se guarda aparte de {@code users} para no alterar el modelo del agregado en esta fase: la fila
 * existe únicamente cuando la cuenta ya verificó su correo.
 */
public interface EmailVerificationRepository {

    Mono<Boolean> isVerified(UUID userId);

    Mono<Void> markVerified(UUID userId, Instant now);
}
