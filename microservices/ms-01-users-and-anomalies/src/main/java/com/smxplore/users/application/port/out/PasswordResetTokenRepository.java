package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.PasswordResetToken;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface PasswordResetTokenRepository {

    Mono<PasswordResetToken> save(PasswordResetToken token);

    Mono<PasswordResetToken> findByTokenHash(String tokenHash);

    Mono<Void> invalidateAllForUser(UUID userId, Instant now);

    /**
     * Elimina los tokens creados antes del corte. Devuelve la cantidad de filas borradas.
     */
    Mono<Long> deleteCreatedBefore(Instant cutoff);
}
