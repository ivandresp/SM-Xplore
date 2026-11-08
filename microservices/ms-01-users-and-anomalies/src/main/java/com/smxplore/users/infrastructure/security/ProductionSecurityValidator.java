package com.smxplore.users.infrastructure.security;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.policy.PasswordPolicy;
import com.smxplore.users.infrastructure.config.BootstrapAdminProperties;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import com.smxplore.users.infrastructure.config.SecurityHeadersProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    private static final Duration MAX_JWT_TTL = Duration.ofHours(12);

    private final JwtProperties jwt;
    private final BootstrapAdminProperties bootstrapAdmin;
    private final CorsProperties cors;
    private final MfaProperties mfa;
    private final SecurityHeadersProperties headers;
    private final InternalApiProperties internal;

    public ProductionSecurityValidator(
            JwtProperties jwt,
            BootstrapAdminProperties bootstrapAdmin,
            CorsProperties cors,
            MfaProperties mfa,
            SecurityHeadersProperties headers,
            InternalApiProperties internal) {
        this.jwt = jwt;
        this.bootstrapAdmin = bootstrapAdmin;
        this.cors = cors;
        this.mfa = mfa;
        this.headers = headers;
        this.internal = internal;
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
                hasUsableJwtKeys(),
                "No hay llaves JWT utilizables: define JWT_PRIVATE_KEY y JWT_PUBLIC_KEY o monta un almacén en "
                        + "JWT_KEY_STORE",
                developmentLike);
        require(
                jwt.ttl() == null || jwt.ttl().compareTo(MAX_JWT_TTL) <= 0,
                "La vigencia del token de acceso no puede superar 12 horas en producción; revisa JWT_TTL",
                developmentLike);
        require(
                !bootstrapAdmin.enabled()
                        || (!isBlank(bootstrapAdmin.password())
                                && !DEFAULT_ADMIN_PASSWORD.equals(bootstrapAdmin.password())
                                && isValidAdminPassword(bootstrapAdmin.password())),
                "El administrador inicial usa una contraseña vacía, por defecto o demasiado débil; configura "
                        + "BOOTSTRAP_ADMIN_PASSWORD fuerte o desactiva BOOTSTRAP_ADMIN_ENABLED",
                developmentLike);
        require(
                mfa.requiredForAdmins(),
                "La autenticación multifactor debe ser obligatoria para administradores fuera de desarrollo",
                developmentLike);
        require(
                !isBlank(mfa.secretKey()),
                "MFA_SECRET_KEY debe estar configurada fuera de desarrollo para cifrar los secretos TOTP",
                developmentLike);
        require(headers.hstsEnabled(), "HSTS debe estar habilitado fuera de desarrollo", developmentLike);
        require(
                internal.configured(),
                "INTERNAL_API_KEY debe estar configurada fuera de desarrollo para firmar las llamadas internas",
                developmentLike);

        List<String> origins = cors.allowedOrigins();
        require(
                !origins.contains("*"),
                "CORS no puede permitir cualquier origen en producción; define CORS_ALLOWED_ORIGINS explícitamente",
                developmentLike);
        require(
                origins.stream().noneMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1")),
                "CORS no puede aceptar orígenes locales fuera de desarrollo",
                developmentLike);
    }

    private boolean hasUsableJwtKeys() {
        if (!isBlank(jwt.privateKey()) && !isBlank(jwt.publicKey())) {
            return true;
        }
        if (!jwt.allowGeneratedKeys()) {
            Path keyStore = jwt.keyStorePath();
            return keyStore != null && Files.isRegularFile(keyStore);
        }
        return false;
    }

    private static boolean isValidAdminPassword(String password) {
        try {
            PasswordPolicy.validate(password);
            return true;
        } catch (ValidationException exception) {
            return false;
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
