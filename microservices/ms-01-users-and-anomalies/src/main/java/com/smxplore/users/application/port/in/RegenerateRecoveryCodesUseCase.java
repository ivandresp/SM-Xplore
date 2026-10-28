package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RecoveryCodesView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface RegenerateRecoveryCodesUseCase {

    /**
     * Reemplaza los códigos de recuperación del usuario previa verificación de contraseña y segundo factor.
     */
    Mono<RecoveryCodesView> regenerate(UUID userId, String password, String code);
}
