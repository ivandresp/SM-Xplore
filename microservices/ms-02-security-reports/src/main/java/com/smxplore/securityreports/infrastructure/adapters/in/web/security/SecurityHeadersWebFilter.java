package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

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
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-site");
        if (exchange.getRequest().getPath().value().startsWith(API_PATH_PREFIX)) {
            headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return chain.filter(exchange);
    }
}
