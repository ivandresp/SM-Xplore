package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ConfirmMfaUseCase {

    Mono<Void> confirm(UUID userId, String code);
}
