package com.smxplore.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.headers")
public record SecurityHeadersProperties(boolean hstsEnabled, int hstsMaxAgeDays) {

    public SecurityHeadersProperties {
        if (hstsMaxAgeDays < 1) {
            throw new IllegalArgumentException("La vigencia de HSTS debe ser al menos de un día");
        }
    }
}
