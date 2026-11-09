package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface PasswordResetTokenRepository {

    Mono<PasswordResetToken> save(PasswordResetToken token);

    Mono<PasswordResetToken> findByTokenHash(String tokenHash);

    Mono<Void> invalidateAllForUser(UUID userId, Instant now);

    /**
     * Invalida únicamente los tokens pendientes de un propósito (MS01-18: no mezclar verificación de correo con
     * recuperación de contraseña).
     */
    Mono<Void> invalidateByPurposeForUser(UUID userId, TokenPurpose purpose, Instant now);

    /**
     * Elimina los tokens creados antes del corte. Devuelve la cantidad de filas borradas.
     */
    Mono<Long> deleteCreatedBefore(Instant cutoff);
}
