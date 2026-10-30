package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface LogoutUseCase {

    /**
     * Revoca todos los tokens emitidos para la cuenta. Al ser JWT sin estado, el cierre de sesión invalida todas las
     * sesiones abiertas de ese usuario.
     */
    Mono<Void> logout(UUID userId);
}
