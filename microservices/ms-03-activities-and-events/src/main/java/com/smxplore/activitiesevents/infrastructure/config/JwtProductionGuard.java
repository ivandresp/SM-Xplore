package com.smxplore.activitiesevents.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Falla al arrancar si el perfil prod aceptaria un JWKS inseguro. El flag
 * allow-insecure-jwks existe solo para el stack local; una variable de entorno
 * podria activarlo por error en produccion y este guard lo impide.
 */
@Component
@RequiredArgsConstructor
public class JwtProductionGuard implements InitializingBean {

    private final JwtProperties properties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (properties.isAllowInsecureJwks()) {
            throw new IllegalStateException(
                    "JWT_ALLOW_INSECURE_JWKS must be false in the prod profile.");
        }
        String uri = properties.getJwkSetUri();
        if (uri != null && uri.regionMatches(true, 0, "http://", 0, 7)) {
            throw new IllegalStateException(
                    "app.security.jwt.jwk-set-uri must use https in the prod profile.");
        }
    }
}
