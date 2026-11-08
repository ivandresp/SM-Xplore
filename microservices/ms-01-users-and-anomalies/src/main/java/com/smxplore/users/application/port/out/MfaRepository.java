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
     * Consume un código de recuperación comparándolo con los hashes con sal almacenados. Devuelve {@code true} si
     * existía, no había sido usado y coincidía (MS01-07: el adaptador es quien conoce el esquema de hash).
     */
    Mono<Boolean> consumeRecoveryCode(UUID userId, String rawCode, Instant now);

    /**
     * Elimina los códigos de recuperación ya usados antes del corte. Devuelve la cantidad de filas borradas.
     */
    Mono<Long> deleteUsedRecoveryCodesBefore(Instant cutoff);
}
