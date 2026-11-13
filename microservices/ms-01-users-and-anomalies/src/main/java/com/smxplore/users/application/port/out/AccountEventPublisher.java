package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/** Publica el bloqueo de una cuenta para que ms-03 despublique su contenido (cascada B3). */
public interface AccountEventPublisher {

    Mono<Void> accountBlocked(Usuario target);
}
