package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.MfaChallengeContent;
import reactor.core.publisher.Mono;

public interface MfaChallengeVerifier {

    /** Valida la firma y vigencia del desafío y devuelve su contenido: usuario, versión de sesión y {@code jti}. */
    Mono<MfaChallengeContent> verify(String token);
}
