package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {"app.security.rate-limit.requests-per-minute=3", "app.security.lockout.enabled=false"})
@Testcontainers
@EnabledIfDockerAvailable
class AuthRateLimitFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/"
                        + POSTGRES.getDatabaseName());
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void rejectsRequestsAboveThePerMinuteLimit() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(forgotPasswordStatus()).isEqualTo(HttpStatus.ACCEPTED);
        }

        Exchange rejected = client.post()
                .uri("/api/v1/auth/forgot-password")
                .body(Map.of("email", "nadie@example.com"))
                .exchange((request, response) -> new Exchange(
                        response.getStatusCode(),
                        response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(rejected.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.retryAfter()).isNotBlank();
        assertThat(rejected.body()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void addsSecurityHeadersToEveryResponse() {
        var headers = client.get().uri("/.well-known/jwks.json").exchange((request, response) -> response.getHeaders());

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("Permissions-Policy")).contains("geolocation=()");
        assertThat(headers.getFirst("Strict-Transport-Security")).isNull();
    }

    private HttpStatusCode forgotPasswordStatus() {
        return client.post()
                .uri("/api/v1/auth/forgot-password")
                .body(Map.of("email", "nadie@example.com"))
                .exchange((request, response) -> response.getStatusCode());
    }

    private record Exchange(HttpStatusCode status, String retryAfter, String body) {}
}
