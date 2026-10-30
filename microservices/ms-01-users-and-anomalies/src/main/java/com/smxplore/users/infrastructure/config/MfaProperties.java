package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.mfa")
public record MfaProperties(boolean requiredForAdmins, Duration challengeTtl, String issuer, String secretKey) {

    public MfaProperties {
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("La vigencia del desafío MFA debe ser positiva");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "SM-Xplore";
        }
    }
}
