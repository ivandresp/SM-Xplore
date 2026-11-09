package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import com.smxplore.securityreports.infrastructure.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-03: el limite cuenta por cuenta (sub del JWT) y cubre todas las escrituras, no solo la creacion de reportes.
 */
class ReportRateLimitWebFilterTests {

    private final RateLimitProperties properties = new RateLimitProperties();
    private final ClientAddressResolver resolver = new ClientAddressResolver(List.of("10.0.0.0/8"));
    private final ReportRateLimitWebFilter filter =
            new ReportRateLimitWebFilter(properties, resolver, new ObjectMapper());

    @Test
    void limitsEachAccountSeparatelyEvenWhenAllRequestsShareTheGatewayAddress() {
        properties.setRequestsPerMinute(2);

        assertThat(call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5")))
                .isNull();
        assertThat(call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5")))
                .isNull();
        assertThat(call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5")))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Bob comparte la IP del proxy pero tiene su propio cupo.
        assertThat(call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "bob", "10.0.0.5")))
                .isNull();
    }

    @Test
    void limitsCommentsWithTheirOwnQuota() {
        properties.setCommentsPerMinute(1);

        assertThat(call(filter, exchange("/api/v1/security-reports/abc/comments", HttpMethod.POST, "alice", "10.0.0.5")))
                .isNull();
        assertThat(call(filter, exchange("/api/v1/security-reports/abc/comments", HttpMethod.POST, "alice", "10.0.0.5")))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // El cupo de comentarios no consume el de creacion de reportes.
        assertThat(call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5")))
                .isNull();
    }

    @Test
    void limitsEveryOtherWriteOnTheReportCollection() {
        properties.setWritesPerMinute(1);

        assertThat(call(filter, exchange("/api/v1/security-reports/abc/photos", HttpMethod.POST, "alice", "10.0.0.5")))
                .isNull();
        assertThat(call(filter, exchange("/api/v1/security-reports/abc/photos", HttpMethod.POST, "alice", "10.0.0.5")))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(call(filter, exchange("/api/v1/security-reports/abc/content", HttpMethod.PATCH, "alice", "10.0.0.5")))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void doesNotLimitReadRequests() {
        properties.setRequestsPerMinute(1);

        assertThat(call(filter, exchange("/api/v1/security-reports/nearby", HttpMethod.GET, "alice", "10.0.0.5")))
                .isNull();
        assertThat(call(filter, exchange("/api/v1/security-reports/nearby", HttpMethod.GET, "alice", "10.0.0.5")))
                .isNull();
    }

    @Test
    void fallsBackToTheClientIpOnlyWhenThereIsNoPrincipal() {
        properties.setRequestsPerMinute(1);

        var first = exchange("/api/v1/security-reports", HttpMethod.POST, null, "8.8.8.8");
        var second = exchange("/api/v1/security-reports", HttpMethod.POST, null, "8.8.8.8");
        var third = exchange("/api/v1/security-reports", HttpMethod.POST, null, "8.8.4.4");

        assertThat(call(filter, first)).isNull();
        assertThat(call(filter, second)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(call(filter, third)).isNull();
    }

    @Test
    void ignoresSpoofedForwardedIpFromUntrustedClients() {
        properties.setRequestsPerMinute(1);

        var spoofedOne = exchange("/api/v1/security-reports", HttpMethod.POST, null, "8.8.8.8", "1.1.1.1");
        var spoofedTwo = exchange("/api/v1/security-reports", HttpMethod.POST, null, "8.8.8.8", "2.2.2.2");

        assertThat(call(filter, spoofedOne)).isNull();
        assertThat(call(filter, spoofedTwo)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void answersWithRetryAfterAndProblemJson() {
        properties.setRequestsPerMinute(1);

        call(filter, exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5"));
        var limited = exchange("/api/v1/security-reports", HttpMethod.POST, "alice", "10.0.0.5");

        assertThat(call(filter, limited)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(limited.getResponse().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
    }

    private static HttpStatus call(ReportRateLimitWebFilter target, ServerWebExchange exchange) {
        WebFilterChain chain = request -> Mono.empty();
        target.filter(exchange, chain).block();
        return exchange.getResponse().getStatusCode() == null
                ? null
                : HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }

    private static ServerWebExchange exchange(String path, HttpMethod method, String subject,
                                              String remoteAddress) {
        return exchange(path, method, subject, remoteAddress, null);
    }

    private static ServerWebExchange exchange(String path, HttpMethod method, String subject,
                                              String remoteAddress, String realIpHeader) {
        var request = MockServerHttpRequest.method(method, URI.create("http://localhost" + path))
                .remoteAddress(new InetSocketAddress(remoteAddress, 55000));
        if (realIpHeader != null) {
            request.header("X-Real-IP", realIpHeader);
        }
        var exchange = MockServerWebExchange.from(request);
        if (subject == null) {
            return exchange;
        }
        return exchange.mutate().principal(Mono.just(authentication(subject))).build();
    }

    private static JwtAuthenticationToken authentication(String subject) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("role", "TOURIST")
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
