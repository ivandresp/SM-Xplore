package com.smxplore.users.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Confirma el correo electrónico de una cuenta a partir del token enviado por correo (MS01-18).
 */
public interface VerifyEmailUseCase {

    Mono<Void> verify(String rawToken);
}
