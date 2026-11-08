package com.smxplore.activitiesevents.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Falla al arrancar si la configuracion de seguridad es insegura.
 *
 * <p>El flag {@code allow-insecure-jwks} es de desarrollo: solo se acepta con un perfil local
 * ({@code local}, {@code dev} o {@code test}). Sin esta comprobacion un contenedor arrancado sin
 * {@code SPRING_PROFILES_ACTIVE} aceptaba un JWKS por HTTP sin que nadie se enterara.
 *
 * <p>Los controles de MFA de administradores y de version de sesion tienen defaults seguros en
 * {@code application.yaml}; fuera de esos perfiles tampoco se pueden apagar. Sin esta comprobacion un
 * despliegue con {@code MFA_REQUIRE_ADMIN=false} aceptaba tokens de ADMIN sin segundo factor y con
 * {@code AUTH_VERSION_ENABLED=false} mantenia vivos los tokens revocados hasta su expiracion.
 *
 * <p>Con el perfil {@code prod} se exige ademas HTTPS, emisor y audiencia: sin audiencia cualquier
 * token del emisor sirve para este servicio.
 */
@Component
@RequiredArgsConstructor
public class JwtProductionGuard implements InitializingBean {

    private static final String HTTP_SCHEME_PREFIX = "http://";
    private static final String AUTH_VERSION_ENABLED = "app.security.auth-version.enabled";
    private static final String MFA_REQUIRE_ADMIN = "app.security.mfa.require-admin";

    private final JwtProperties properties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        boolean local = environment.acceptsProfiles(Profiles.of("local", "dev", "test"));
        if (properties.isAllowInsecureJwks() && !local) {
            throw new IllegalStateException("JWT_ALLOW_INSECURE_JWKS is a development flag: it requires"
                    + " the local, dev or test profile. Set SPRING_PROFILES_ACTIVE=local for the local"
                    + " stack, or disable it and serve the JWKS over HTTPS.");
        }
        if (!local) {
            requireEnabled(AUTH_VERSION_ENABLED, "AUTH_VERSION_ENABLED");
            requireEnabled(MFA_REQUIRE_ADMIN, "MFA_REQUIRE_ADMIN");
        }
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (properties.isAllowInsecureJwks()) {
            throw new IllegalStateException(
                    "JWT_ALLOW_INSECURE_JWKS must be false in the prod profile.");
        }
        String uri = properties.getJwkSetUri();
        if (uri != null && uri.regionMatches(true, 0, HTTP_SCHEME_PREFIX, 0, HTTP_SCHEME_PREFIX.length())) {
            throw new IllegalStateException(
                    "app.security.jwt.jwk-set-uri must use https in the prod profile.");
        }
        if (!StringUtils.hasText(properties.getIssuer())) {
            throw new IllegalStateException("app.security.jwt.issuer is required in the prod profile.");
        }
        if (!StringUtils.hasText(properties.getAudience())) {
            throw new IllegalStateException(
                    "app.security.jwt.audience is required in the prod profile.");
        }
    }

    /**
     * Un control apagado fuera de local/dev/test no es una eleccion de desarrollo: es un despliegue
     * sin segundo factor para administradores o sin revocacion de sesiones. Preferimos no arrancar.
     */
    private void requireEnabled(String property, String variable) {
        if (!Boolean.TRUE.equals(environment.getProperty(property, Boolean.class))) {
            throw new IllegalStateException(variable + " must be true outside the local, dev or test"
                    + " profiles: both controls are safe defaults, disable them only for development"
                    + " (SPRING_PROFILES_ACTIVE=local).");
        }
    }
}
