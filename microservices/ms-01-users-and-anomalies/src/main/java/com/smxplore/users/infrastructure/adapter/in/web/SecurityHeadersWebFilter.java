package com.smxplore.users.infrastructure.adapter.in.web;

import com.smxplore.users.infrastructure.config.SecurityHeadersProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Cabeceras de endurecimiento del navegador. HSTS sólo se activa cuando el servicio se publica detrás de TLS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersWebFilter implements WebFilter {

    private final SecurityHeadersProperties properties;

    public SecurityHeadersWebFilter(SecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");

        if (properties.hstsEnabled()) {
            headers.set(
                    "Strict-Transport-Security",
                    "max-age=" + (properties.hstsMaxAgeDays() * 86_400L) + "; includeSubDomains");
        }
        if (exchange.getRequest().getPath().value().startsWith("/api/v1/auth")) {
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return chain.filter(exchange);
    }
}
