package com.smxplore.users.shared.infrastructure.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.password-reset")
public record PasswordResetProperties(Duration ttl) {

    public PasswordResetProperties {
        Objects.requireNonNull(ttl, "La vigencia del token de recuperación es obligatoria");
    }
}
