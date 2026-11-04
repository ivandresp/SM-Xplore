package com.smxplore.users.infrastructure.security;

import com.smxplore.users.infrastructure.config.BootstrapAdminProperties;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifica que una configuración de producción no arranque con los valores de desarrollo. Se ejecuta al inicio y
 * detiene el arranque ante configuraciones inseguras.
 */
public final class ProductionSecurityValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionSecurityValidator.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe123!";

    private final JwtProperties jwt;
    private final BootstrapAdminProperties bootstrapAdmin;
    private final CorsProperties cors;
    private final MfaProperties mfa;

    public ProductionSecurityValidator(
            JwtProperties jwt, BootstrapAdminProperties bootstrapAdmin, CorsProperties cors, MfaProperties mfa) {
        this.jwt = jwt;
        this.bootstrapAdmin = bootstrapAdmin;
        this.cors = cors;
        this.mfa = mfa;
    }

    public void validate() {
        if (jwt.allowGeneratedKeys() && isBlank(jwt.privateKey())) {
            throw new IllegalStateException(
                    "En producción las llaves JWT deben configurarse con JWT_PRIVATE_KEY y JWT_PUBLIC_KEY; "
                            + "no se permite generarlas al arrancar");
        }
        if (bootstrapAdmin.enabled() && DEFAULT_ADMIN_PASSWORD.equals(bootstrapAdmin.password())) {
            throw new IllegalStateException(
                    "El administrador inicial usa la contraseña por defecto; configura BOOTSTRAP_ADMIN_PASSWORD "
                            + "o desactiva BOOTSTRAP_ADMIN_ENABLED");
        }
        if (!mfa.requiredForAdmins()) {
            LOGGER.warn("La autenticación multifactor no es obligatoria para administradores en producción");
        }
        List<String> origins = cors.allowedOrigins();
        if (origins.stream().anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            LOGGER.warn("CORS acepta orígenes locales en producción: {}", origins);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
