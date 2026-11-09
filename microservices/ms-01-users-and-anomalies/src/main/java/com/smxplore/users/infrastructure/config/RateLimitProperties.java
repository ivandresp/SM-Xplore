package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled, int requestsPerMinute, int accountMaxAttempts, Duration accountWindow) {

    /** Intentos fallidos por cuenta permitidos en la ventana antes de responder 429. */
    static final int DEFAULT_ACCOUNT_MAX_ATTEMPTS = 10;

    static final Duration DEFAULT_ACCOUNT_WINDOW = Duration.ofMinutes(15);

    public RateLimitProperties {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("El límite de solicitudes de autenticación debe ser positivo");
        }
        accountMaxAttempts = accountMaxAttempts < 1 ? DEFAULT_ACCOUNT_MAX_ATTEMPTS : accountMaxAttempts;
        accountWindow = accountWindow == null || accountWindow.isZero() || accountWindow.isNegative()
                ? DEFAULT_ACCOUNT_WINDOW
                : accountWindow;
    }
}
