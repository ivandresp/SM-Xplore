package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Cabeceras de endurecimiento de la respuesta.
 *
 * <p>MS02-06: el servicio solo devuelve JSON, pero si un cliente web renderizara el contenido como HTML el riesgo
 * residual es XSS, asi que ademas de rechazar HTML en el dominio se envia una CSP restrictiva y {@code no-store}
 * en las respuestas de la API (contienen datos personales de quien reporta).
 *
 * <p>MS02-12: {@code Pragma: no-cache} para clientes HTTP/1.0 y {@code Strict-Transport-Security} cuando el
 * servicio se sirve detras de un terminador TLS ({@code HSTS_ENABLED=true}); si el borde ya lo envia, se deja
 * apagado para no duplicarlo.
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
    private static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains";

    private final boolean hstsEnabled;

    public SecurityHeadersWebFilter(@Value("${app.security.headers.hsts:false}") boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-site");
        if (hstsEnabled) {
            headers.set("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        }
        if (exchange.getRequest().getPath().value().startsWith(API_PATH_PREFIX)) {
            headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.set(HttpHeaders.PRAGMA, "no-cache");
        }
        return chain.filter(exchange);
    }
}
