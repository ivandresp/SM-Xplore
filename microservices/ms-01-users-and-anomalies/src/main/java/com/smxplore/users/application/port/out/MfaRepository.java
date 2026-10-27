package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface MfaRepository {

    Mono<MfaEnrollment> findByUserId(UUID userId);

    Mono<Void> save(MfaEnrollment enrollment);

    Mono<Void> deleteByUserId(UUID userId);

    /**
     * Registra el paso TOTP utilizado. Devuelve {@code true} si el paso es nuevo, lo que impide reutilizar un código.
     */
    Mono<Boolean> claimStep(UUID userId, long step);

    Mono<Void> replaceRecoveryCodes(UUID userId, List<String> codeHashes, Instant now);

    /**
     * Consume un código de recuperación. Devuelve {@code true} si existía y no había sido usado.
     */
    Mono<Boolean> consumeRecoveryCode(UUID userId, String codeHash, Instant now);
}
