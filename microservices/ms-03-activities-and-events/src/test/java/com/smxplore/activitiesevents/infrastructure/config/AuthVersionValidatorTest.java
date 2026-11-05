package com.smxplore.activitiesevents.infrastructure.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthVersionValidatorTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rejectsAnAdministratorWithoutMfaWhenRequired() {
        AuthVersionValidator validator = validator(properties(false, null), true);

        assertThatThrownBy(() -> validator.validate(jwt("ADMIN", false, 1)).block())
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("MFA");
    }

    @Test
    void acceptsAMatchingAuthVersionAndCachesIt() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(2, calls);
        AuthVersionValidator validator = validator(properties(true, baseUrl()), false);
        Jwt jwt = jwt("TOURIST", true, 2);

        assertThat(validator.validate(jwt).block()).isSameAs(jwt);
        assertThat(validator.validate(jwt).block()).isSameAs(jwt);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsAStaleAuthVersion() throws IOException {
        startServer(3, new AtomicInteger());
        AuthVersionValidator validator = validator(properties(true, baseUrl()), false);

        assertThatThrownBy(() -> validator.validate(jwt("TOURIST", true, 2)).block())
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("session");
    }

    @Test
    void acceptsATokenNewerThanTheCachedVersion() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong current = new java.util.concurrent.atomic.AtomicLong(2);
        startServer(current::get, calls);
        AuthVersionValidator validator = validator(properties(true, baseUrl()), false);

        assertThat(validator.validate(jwt("TOURIST", true, 2)).block()).isNotNull();
        current.set(3);

        assertThat(validator.validate(jwt("TOURIST", true, 3)).block()).isNotNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void skipsTheRemoteCheckWhenDisabled() {
        AuthVersionValidator validator = validator(properties(false, null), false);

        Jwt jwt = jwt("TOURIST", false, 7);

        assertThat(validator.validate(jwt).block()).isSameAs(jwt);
    }

    private AuthVersionValidator validator(AuthVersionProperties properties, boolean requireAdminMfa) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRoleClaim("role");
        return new AuthVersionValidator(properties, jwtProperties, requireAdminMfa);
    }

    private AuthVersionProperties properties(boolean enabled, String baseUrl) {
        AuthVersionProperties properties = new AuthVersionProperties();
        properties.setEnabled(enabled);
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setCacheTtl(Duration.ofSeconds(60));
        properties.validate();
        return properties;
    }

    private Jwt jwt(String role, boolean mfa, long version) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", role)
                .claim("mfa", mfa)
                .claim("ver", version)
                .build();
    }

    private void startServer(long currentVersion, AtomicInteger calls) throws IOException {
        startServer(() -> currentVersion, calls);
    }

    private void startServer(java.util.function.LongSupplier currentVersion, AtomicInteger calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/users/me/auth-version", exchange -> {
            calls.incrementAndGet();
            byte[] body = ("{\"authVersion\":" + currentVersion.getAsLong() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}