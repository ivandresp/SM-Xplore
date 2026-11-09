package com.smxplore.securityreports.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Limites de escritura por cuenta (y por IP como respaldo cuando no hay principal).
 *
 * <p>El limite por IP de la version anterior era global detras del gateway (todas las peticiones llegan con la
 * IP del proxy) y solo cubria la creacion de reportes. Ahora cada tipo de escritura tiene su propio cupo y la
 * clave principal es el {@code sub} del JWT, que si distingue usuarios (MS02-03).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    /** Reportes creados por minuto y por cuenta. */
    private int requestsPerMinute = 30;
    /** Comentarios creados por minuto y por cuenta. */
    private int commentsPerMinute = 30;
    /** Resto de escrituras (fotos, contenido, cierre, resolucion) por minuto y por cuenta. */
    private int writesPerMinute = 60;
    /**
     * Proxies de confianza que pueden informar {@code X-Real-IP}. Fuera de ellos se ignora el encabezado y se
     * usa la direccion de la conexion.
     */
    private List<String> trustedProxies = List.of(
            "127.0.0.1/32", "::1/128", "172.16.0.0/12", "10.0.0.0/8", "192.168.0.0/16");

    /** No admite valores no positivos: un limite 0 bloquearia todas las escrituras. */
    public int effectiveRequestsPerMinute() {
        return Math.max(1, requestsPerMinute);
    }

    public int effectiveCommentsPerMinute() {
        return Math.max(1, commentsPerMinute);
    }

    public int effectiveWritesPerMinute() {
        return Math.max(1, writesPerMinute);
    }
}
