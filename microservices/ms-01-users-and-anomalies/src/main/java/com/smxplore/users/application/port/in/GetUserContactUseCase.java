package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.UserContactView;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Caso de uso interno: resolver el contacto de una cuenta por su identificador. */
public interface GetUserContactUseCase {

    Mono<UserContactView> get(UUID userId);
}
