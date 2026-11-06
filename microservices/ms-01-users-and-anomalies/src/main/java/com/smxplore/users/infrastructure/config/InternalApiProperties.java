package com.smxplore.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clave compartida para el endpoint interno de contactos ({@code /internal/**}).
 *
 * <p>Se configura con la variable de entorno {@code INTERNAL_API_KEY}. Una clave vacía significa
 * "endpoint deshabilitado": las peticiones responden {@code 503} en lugar de quedar abiertas, así que
 * olvidar la variable no expone datos. Es un mecanismo provisional; P3-8 lo reemplaza por autenticación
 * servicio a servicio.
 */
@ConfigurationProperties("app.internal")
public record InternalApiProperties(String apiKey) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
