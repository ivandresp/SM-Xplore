package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del endpoint interno de contactos.
 *
 * <p>MS01-11: la clave compartida {@code INTERNAL_API_KEY} (cabecera {@code X-Internal-Api-Key}) se sustituyó por una
 * firma HMAC por servicio. {@code apiKey} pasa a ser el secreto maestro del que se deriva una clave distinta para cada
 * servicio y {@code allowedServices} permite una lista blanca explícita.
 */
@ConfigurationProperties("app.internal")
public record InternalApiProperties(
        String apiKey, List<String> allowedServices, Duration maxClockSkew, int rateLimitPerMinute) {

    private static final Duration DEFAULT_MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;

    public InternalApiProperties {
        allowedServices = allowedServices == null ? List.of() : List.copyOf(allowedServices);
        maxClockSkew = maxClockSkew == null ? DEFAULT_MAX_CLOCK_SKEW : maxClockSkew;
        if (maxClockSkew.isNegative() || maxClockSkew.isZero()) {
            throw new IllegalArgumentException("La tolerancia de reloj interna debe ser positiva");
        }
        if (rateLimitPerMinute < 1) {
            rateLimitPerMinute = DEFAULT_RATE_LIMIT_PER_MINUTE;
        }
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
