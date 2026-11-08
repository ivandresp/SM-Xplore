package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-04 / MS02-06: cabeceras de endurecimiento y {@code no-store} en las respuestas de la API.
 */
class SecurityHeadersWebFilterTests {

    private final SecurityHeadersWebFilter filter = new SecurityHeadersWebFilter();

    @Test
    void addsTheHardeningHeadersToApiResponses() {
        var exchange = filter(MockServerHttpRequest.get("/api/v1/security-reports").build());
        var headers = exchange.getResponse().getHeaders();

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("Permissions-Policy")).contains("geolocation=()");
        assertThat(headers.getFirst("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(headers.getFirst("Cross-Origin-Resource-Policy")).isEqualTo("same-site");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void doesNotDisableCachingForNonApiDocumentation() {
        var exchange = filter(MockServerHttpRequest.get("/swagger-ui/index.html").build());
        var headers = exchange.getResponse().getHeaders();

        assertThat(headers.getFirst("Content-Security-Policy")).isNull();
        assertThat(headers.getFirst("Cache-Control")).isNull();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    private MockServerWebExchange filter(MockServerHttpRequest request) {
        var exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = current -> Mono.empty();
        filter.filter(exchange, chain).block();
        return exchange;
    }
}
