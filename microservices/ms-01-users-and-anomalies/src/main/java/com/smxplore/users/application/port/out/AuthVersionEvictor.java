package com.smxplore.users.application.port.out;

import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Descarta la versión de autenticación cacheada para que una revocación de sesión surta efecto de inmediato.
 */
public interface AuthVersionEvictor {

    Mono<Void> evict(UUID userId);
}
