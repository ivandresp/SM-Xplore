package com.smxplore.users.infrastructure.adapter.in.web;

import com.smxplore.users.application.port.out.RequestRateSignal;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Alimenta el contador de solicitudes que usa la detección de picos anómalos. No cuenta los endpoints técnicos de
 * actuator, documentación ni JWKS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestRateWebFilter implements WebFilter {

    private final RequestRateSignal requestRate;

    public RequestRateWebFilter(RequestRateSignal requestRate) {
        this.requestRate = requestRate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isTechnicalEndpoint(exchange)) {
            return chain.filter(exchange);
        }
        return requestRate.record().then(chain.filter(exchange));
    }

    private static boolean isTechnicalEndpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/.well-known");
    }
}
