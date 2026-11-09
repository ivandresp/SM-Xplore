package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ConfirmMfaUseCase {

    /**
     * Activa la inscripción TOTP pendiente. Exige la contraseña actual y revoca las sesiones existentes al confirmar.
     */
    Mono<Void> confirm(UUID userId, String code, String password);
}
