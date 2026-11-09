package com.smxplore.users.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.infrastructure.config.ClientAddressProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ClientAddressResolverTest {

    private static final ClientAddressResolver RESOLVER =
            new ClientAddressResolver(new ClientAddressProperties(List.of("10.0.0.0/8", "127.0.0.1/32", "::1/128")));

    @Test
    void usesTheConnectionAddressWhenThePeerIsNotATrustedProxy() {
        MockServerWebExchange exchange = exchange("203.0.113.9", "1.2.3.4", "9.9.9.9");

        assertThat(RESOLVER.resolve(exchange)).isEqualTo("203.0.113.9");
    }

    @Test
    void acceptsXRealIpOnlyFromATrustedProxy() {
        MockServerWebExchange exchange = exchange("10.1.2.3", "198.51.100.7", null);

        assertThat(RESOLVER.resolve(exchange)).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresXForwardedForEvenFromATrustedProxy() {
        MockServerWebExchange exchange = exchange("10.1.2.3", null, "198.51.100.7");

        assertThat(RESOLVER.resolve(exchange)).isEqualTo("10.1.2.3");
    }

    @Test
    void fallsBackToTheProxyAddressWhenXRealIpIsNotAnIpLiteral() {
        MockServerWebExchange exchange = exchange("10.1.2.3", "not-an-ip", null);

        assertThat(RESOLVER.resolve(exchange)).isEqualTo("10.1.2.3");
    }

    @Test
    void returnsDesconocidoWhenThereIsNoRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login"));

        assertThat(RESOLVER.resolve(exchange)).isEqualTo("desconocido");
    }

    @Test
    void rejectsAnInvalidTrustedProxyEntry() {
        assertThatThrownBy(() -> new ClientAddressResolver(new ClientAddressProperties(List.of("no-es-red"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockServerWebExchange exchange(String remote, String realIp, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get("/api/v1/auth/login").remoteAddress(new InetSocketAddress(remote, 4567));
        if (realIp != null) {
            builder.header("X-Real-IP", realIp);
        }
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
