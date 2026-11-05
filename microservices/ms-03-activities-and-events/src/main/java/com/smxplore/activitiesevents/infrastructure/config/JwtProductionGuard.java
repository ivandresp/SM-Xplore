package com.smxplore.activitiesevents.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Falla al arrancar si la configuracion de JWT es insegura.
 *
 * <p>El flag {@code allow-insecure-jwks} es de desarrollo: solo se acepta con un perfil local
 * ({@code local}, {@code dev} o {@code test}). Sin esta comprobacion un contenedor arrancado sin
 * {@code SPRING_PROFILES_ACTIVE} aceptaba un JWKS por HTTP sin que nadie se enterara.
 *
 * <p>Con el perfil {@code prod} se exige ademas HTTPS, emisor y audiencia: sin audiencia cualquier
 * token del emisor sirve para este servicio.
 */
@Component
@RequiredArgsConstructor
public class JwtProductionGuard implements InitializingBean {

    private static final String HTTP_SCHEME_PREFIX = "http://";

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
}
