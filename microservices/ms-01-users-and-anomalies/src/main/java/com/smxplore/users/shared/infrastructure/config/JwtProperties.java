package com.smxplore.users.shared.infrastructure.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String issuer, String audience, Duration ttl, String keyId) {

    public JwtProperties {
        Objects.requireNonNull(issuer, "El emisor JWT es obligatorio");
        Objects.requireNonNull(audience, "La audiencia JWT es obligatoria");
        Objects.requireNonNull(ttl, "La vigencia JWT es obligatoria");
        Objects.requireNonNull(keyId, "El identificador de llave JWT es obligatorio");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("La vigencia JWT debe ser positiva");
        }
    }
}
