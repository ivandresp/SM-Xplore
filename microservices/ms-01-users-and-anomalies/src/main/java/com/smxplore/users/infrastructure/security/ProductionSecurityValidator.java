package com.smxplore.users.infrastructure.security;

import com.smxplore.users.infrastructure.config.BootstrapAdminProperties;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import com.smxplore.users.infrastructure.config.SecurityHeadersProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifica que el arranque no use los valores de desarrollo salvo en perfiles explícitamente locales. En perfiles que
 * no son {@code dev}, {@code local} ni {@code test} cualquier configuración insegura detiene el arranque.
 */
public final class ProductionSecurityValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionSecurityValidator.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    private final JwtProperties jwt;
    private final BootstrapAdminProperties bootstrapAdmin;
    private final CorsProperties cors;
    private final MfaProperties mfa;
    private final SecurityHeadersProperties headers;

    public ProductionSecurityValidator(
            JwtProperties jwt,
            BootstrapAdminProperties bootstrapAdmin,
            CorsProperties cors,
            MfaProperties mfa,
            SecurityHeadersProperties headers) {
        this.jwt = jwt;
        this.bootstrapAdmin = bootstrapAdmin;
        this.cors = cors;
        this.mfa = mfa;
        this.headers = headers;
    }

    /**
     * @param developmentLike {@code true} cuando el perfil activo es dev, local o test; en ese caso las
     *     configuraciones inseguras solo se registran como advertencia.
     */
    public void validate(boolean developmentLike) {
        require(
                !jwt.allowGeneratedKeys() || !isBlank(jwt.privateKey()),
                "En producción las llaves JWT deben configurarse con JWT_PRIVATE_KEY y JWT_PUBLIC_KEY; no se permite "
                        + "generarlas al arrancar",
                developmentLike);
        require(
                !jwt.allowInsecureJwks(),
                "El JWKS público no puede distribuirse con llaves de ejemplo o inseguras en producción; configura "
                        + "JWT_ALLOW_INSECURE_JWKS=false",
                developmentLike);
        require(
                !bootstrapAdmin.enabled()
                        || (!isBlank(bootstrapAdmin.password())
                                && !DEFAULT_ADMIN_PASSWORD.equals(bootstrapAdmin.password())),
                "El administrador inicial usa la contraseña por defecto o vacía; configura "
                        + "BOOTSTRAP_ADMIN_PASSWORD o desactiva BOOTSTRAP_ADMIN_ENABLED",
                developmentLike);
        require(
                mfa.requiredForAdmins(),
                "La autenticación multifactor debe ser obligatoria para administradores fuera de desarrollo",
                developmentLike);
        require(headers.hstsEnabled(), "HSTS debe estar habilitado fuera de desarrollo", developmentLike);
        List<String> origins = cors.allowedOrigins();
        if (origins.stream().anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            LOGGER.warn("CORS acepta orígenes locales: {}", origins);
        }
    }

    private static void require(boolean safe, String message, boolean developmentLike) {
        if (safe) {
            return;
        }
        if (developmentLike) {
            LOGGER.warn(message);
            return;
        }
        throw new IllegalStateException(message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
