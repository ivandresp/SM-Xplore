package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface DisableMfaUseCase {

    Mono<Void> disable(UUID userId, String password, String code);
}
