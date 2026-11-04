package com.smxplore.users.application.port.out;

import java.time.Instant;
import reactor.core.publisher.Mono;

/**
 * Contador de solicitudes HTTP atendidas por esta instancia del servicio.
 */
public interface RequestRateSignal {

    Mono<Void> record();

    Mono<Long> countSince(Instant since);
}
