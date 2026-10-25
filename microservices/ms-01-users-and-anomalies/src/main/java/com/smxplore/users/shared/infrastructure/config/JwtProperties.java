package com.smxplore.users.shared.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration ttl,
        String privateKey,
        String publicKey,
        Path keyStorePath,
        boolean allowGeneratedKeys) {

    public JwtProperties {
        Objects.requireNonNull(issuer, "El emisor JWT es obligatorio");
        Objects.requireNonNull(audience, "La audiencia JWT es obligatoria");
        Objects.requireNonNull(ttl, "La vigencia JWT es obligatoria");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("La vigencia JWT debe ser positiva");
        }
        boolean hasPrivateKey = privateKey != null && !privateKey.isBlank();
        boolean hasPublicKey = publicKey != null && !publicKey.isBlank();
        if (hasPrivateKey != hasPublicKey) {
            throw new IllegalArgumentException("JWT_PRIVATE_KEY y JWT_PUBLIC_KEY deben configurarse juntas");
        }
    }
}
