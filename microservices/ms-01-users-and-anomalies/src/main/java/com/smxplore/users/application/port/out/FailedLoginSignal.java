package com.smxplore.users.application.port.out;

import java.time.Instant;
import reactor.core.publisher.Mono;

/**
 * Registro de intentos de inicio de sesión fallidos. No almacena el correo ni la contraseña; solo marcas de tiempo.
 */
public interface FailedLoginSignal {

    Mono<Void> record();

    Mono<Long> countSince(Instant since);
}
