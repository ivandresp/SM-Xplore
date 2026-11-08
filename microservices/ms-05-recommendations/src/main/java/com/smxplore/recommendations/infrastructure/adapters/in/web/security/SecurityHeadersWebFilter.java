package com.smxplore.recommendations.infrastructure.adapters.in.web.security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Cabeceras de seguridad de las respuestas de ms-05.
 *
 * <p>MS05-11: se añade una CSP mínima ({@code default-src 'none'}) porque el servicio solo devuelve
 * JSON y nunca sirve documentos o scripts, y HSTS cuando la petición llega por TLS (directo o a
 * través del gateway, que reescribe {@code X-Forwarded-Proto}). En producción el gateway también
 * debe enviar {@code Strict-Transport-Security} en su terminación TLS: ms-05 no puede cubrir el
 * primer acceso.</p>
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    private static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-site");
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (isSecure(exchange)) {
            headers.set("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        }
        return chain.filter(exchange);
    }

    private static boolean isSecure(ServerWebExchange exchange) {
        if (exchange.getRequest().getSslInfo() != null) {
            return true;
        }
        // El gateway termina TLS y reescribe X-Forwarded-Proto; el cliente no puede falsificarlo
        // a través de él. Sobre HTTP la cabecera HSTS la ignoran los navegadores, así que aceptarla
        // también no debilita nada.
        String forwardedProto =
                exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
