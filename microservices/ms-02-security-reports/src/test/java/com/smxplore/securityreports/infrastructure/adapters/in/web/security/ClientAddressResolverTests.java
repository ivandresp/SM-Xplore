package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-03: solo un proxy de confianza puede informar la IP del cliente con {@code X-Real-IP}.
 */
class ClientAddressResolverTests {

    private final ClientAddressResolver resolver = new ClientAddressResolver(List.of("10.0.0.0/8"));

    @Test
    void usesTheConnectionAddressForUntrustedClients() {
        var exchange = exchange("8.8.8.8", "1.2.3.4");

        assertThat(resolver.resolve(exchange)).isEqualTo("8.8.8.8");
    }

    @Test
    void trustsTheForwardedAddressFromATrustedProxy() {
        var exchange = exchange("10.1.2.3", "1.2.3.4");

        assertThat(resolver.resolve(exchange)).isEqualTo("1.2.3.4");
    }

    @Test
    void ignoresMalformedForwardedValues() {
        var exchange = exchange("10.1.2.3", "not-an-ip; DROP TABLE");

        assertThat(resolver.resolve(exchange)).isEqualTo("10.1.2.3");
    }

    @Test
    void reportsUnknownWhenTheAddressIsMissing() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/security-reports").build());

        assertThat(resolver.resolve(exchange)).isEqualTo("unknown");
    }

    private static MockServerWebExchange exchange(String remoteAddress, String realIp) {
        var request = MockServerHttpRequest.get("/api/v1/security-reports")
                .remoteAddress(new InetSocketAddress(remoteAddress, 50000))
                .header("X-Real-IP", realIp);
        return MockServerWebExchange.from(request);
    }
}
