package com.smxplore.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(boolean enabled, int requestsPerMinute) {

    public RateLimitProperties {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("El límite de solicitudes de autenticación debe ser positivo");
        }
    }
}
