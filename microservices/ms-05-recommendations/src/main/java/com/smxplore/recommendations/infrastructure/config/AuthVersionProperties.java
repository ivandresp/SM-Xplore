package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración para validar el claim {@code ver} contra la versión de autenticación de ms-01.
 * En producción debe estar habilitada; en local/tests queda apagada por defecto.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.auth-version")
public class AuthVersionProperties {

    private boolean enabled;
    private String baseUrl;
    private Duration timeout = Duration.ofSeconds(2);
    private Duration cacheTtl = Duration.ofSeconds(60);

    @PostConstruct
    public void validate() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(2);
        }
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            cacheTtl = Duration.ofSeconds(60);
        }
        if (baseUrl != null) {
            baseUrl = stripTrailingSlash(baseUrl.trim());
        }
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException(
                    "app.security.auth-version.base-url is required when auth version validation is enabled");
        }
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
