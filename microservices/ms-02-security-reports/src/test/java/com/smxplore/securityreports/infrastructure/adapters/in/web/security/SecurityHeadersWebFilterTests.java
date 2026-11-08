package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-04 / MS02-06 / MS02-12: cabeceras de endurecimiento, {@code no-store} y HSTS opcional.
 */
class SecurityHeadersWebFilterTests {

    private final SecurityHeadersWebFilter filter = new SecurityHeadersWebFilter(false);

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
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void doesNotDisableCachingForNonApiDocumentation() {
        var exchange = filter(MockServerHttpRequest.get("/swagger-ui/index.html").build());
        var headers = exchange.getResponse().getHeaders();

        assertThat(headers.getFirst("Content-Security-Policy")).isNull();
        assertThat(headers.getFirst("Cache-Control")).isNull();
        assertThat(headers.getFirst("Pragma")).isNull();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void addsHstsOnlyWhenEnabled() {
        var exchangeWithHsts = exchange(new SecurityHeadersWebFilter(true),
                MockServerHttpRequest.get("/api/v1/security-reports").build());

        assertThat(exchangeWithHsts.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(filter(MockServerHttpRequest.get("/api/v1/security-reports").build())
                .getResponse().getHeaders().getFirst("Strict-Transport-Security")).isNull();
    }

    private MockServerWebExchange filter(MockServerHttpRequest request) {
        return exchange(filter, request);
    }

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return exchange(filter, request);
    }

    private MockServerWebExchange exchange(SecurityHeadersWebFilter target, MockServerHttpRequest request) {
        var exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = current -> Mono.empty();
        target.filter(exchange, chain).block();
        return exchange;
    }
}
