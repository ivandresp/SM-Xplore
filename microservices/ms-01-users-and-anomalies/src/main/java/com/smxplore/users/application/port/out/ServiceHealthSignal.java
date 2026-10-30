package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.ServiceHealthStatus;
import reactor.core.publisher.Mono;

/**
 * Verificación de disponibilidad de las dependencias del servicio (PostgreSQL y MongoDB).
 */
public interface ServiceHealthSignal {

    Mono<ServiceHealthStatus> check();
}
