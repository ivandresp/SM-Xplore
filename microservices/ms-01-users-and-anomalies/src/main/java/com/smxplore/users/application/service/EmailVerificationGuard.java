package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.application.port.out.EmailVerificationRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Puerta para acciones sensibles que exigen correo verificado (MS01-18).
 *
 * <p>Decisión de alcance: en esta fase solo se exige verificación para publicar reseñas (la acción con más abuso
 * posible desde cuentas desechables). El usuario puede iniciar sesión y editar su perfil sin verificar, pero no puede
 * crear contenido público.
 */
public final class EmailVerificationGuard {

    private final EmailVerificationRepository verifications;
    private final EmailVerificationPolicy properties;

    public EmailVerificationGuard(EmailVerificationRepository verifications, EmailVerificationPolicy properties) {
        this.verifications = verifications;
        this.properties = properties;
    }

    public Mono<Void> ensureVerified(UUID userId) {
        if (!properties.enabled()) {
            return Mono.empty();
        }
        return verifications
                .isVerified(userId)
                .flatMap(verified -> verified
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException(
                                "Debes verificar tu correo electrónico antes de publicar reseñas")));
    }
}
