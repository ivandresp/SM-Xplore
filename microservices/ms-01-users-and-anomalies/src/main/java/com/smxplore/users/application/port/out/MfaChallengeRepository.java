package com.smxplore.users.application.port.out;

import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Registro persistente de los desafíos MFA emitidos.
 *
 * <p>MS01-06 / MS01-08: el desafío es un JWT de vida corta, pero antes era reutilizable durante toda su vigencia y no
 * estaba ligado a la versión de sesión del usuario. Persistir el {@code jti} permite consumirlo una sola vez de forma
 * atómica.
 */
public interface MfaChallengeRepository {

    Mono<Void> save(UUID jti, UUID userId, Instant expiresAt);

    /** Marca el desafío como usado; devuelve {@code true} solo si esta llamada lo consumió. */
    Mono<Boolean> consume(UUID jti, Instant now);

    Mono<Long> deleteExpiredBefore(Instant cutoff);
}
