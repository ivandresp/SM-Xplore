package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.MfaEnrollmentView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface EnrollMfaUseCase {

    /**
     * Inicia la inscripción TOTP. Exige la contraseña actual para que un token robado no permita inscribir un segundo
     * factor controlado por el atacante.
     */
    Mono<MfaEnrollmentView> enroll(UUID userId, String password);
}
