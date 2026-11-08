package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Normaliza el {@code X-Correlation-Id} que envian los clientes (MS02-08).
 *
 * <p>El valor viaja al evento del outbox y a Kafka, asi que un cliente podia inflar el documento con megabytes o
 * inyectar caracteres de control en los logs. Se acepta solo un identificador acotado (1..128 caracteres
 * alfanumericos y {@code . _ : -}); cuando no cumple se genera uno nuevo en lugar de rechazar la peticion.
 */
public final class CorrelationIdResolver {

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private CorrelationIdResolver() {
    }

    public static String resolve(String headerValue) {
        if (headerValue == null) {
            return UUID.randomUUID().toString();
        }
        String candidate = headerValue.trim();
        return VALID_CORRELATION_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
