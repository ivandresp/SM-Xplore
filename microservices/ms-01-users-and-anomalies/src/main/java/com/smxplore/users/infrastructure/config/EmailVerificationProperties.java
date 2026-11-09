package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la verificación de correo (MS01-18).
 *
 * <p>{@code enabled} se activa por defecto: cerrar el registro a cuentas sin correo comprobado es el comportamiento
 * seguro. En pruebas se desactiva explícitamente para no exigir el flujo de Mailpit en cada suite.
 */
@ConfigurationProperties("app.registration.email-verification")
public record EmailVerificationProperties(boolean enabled, Duration tokenTtl) {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(24);

    public EmailVerificationProperties {
        if (tokenTtl == null) {
            tokenTtl = DEFAULT_TOKEN_TTL;
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("La vigencia del token de verificación de correo debe ser positiva");
        }
    }
}
