package com.smxplore.users.application.port.out;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface MfaChallengeVerifier {

    /**
     * Valida un token de desafío MFA y devuelve el identificador del usuario.
     */
    Mono<UUID> verify(String token);
}
