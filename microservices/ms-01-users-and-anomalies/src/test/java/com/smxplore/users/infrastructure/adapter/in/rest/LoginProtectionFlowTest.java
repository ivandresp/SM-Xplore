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
import org.springframework.http.ResponseEntity;
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
        properties = {"app.security.lockout.max-attempts=3", "app.security.rate-limit.requests-per-minute=120"})
@Testcontainers
@EnabledIfDockerAvailable
class LoginProtectionFlowTest {

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
    void locksAccountAfterRepeatedFailures() {
        register("bloqueo@example.com", "1000000001", "3001112201");

        assertThat(loginStatus("bloqueo@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("bloqueo@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("bloqueo@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);

        Exchange locked = login("bloqueo@example.com", "ChangeMe123!");
        assertThat(locked.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(locked.retryAfter()).isNotBlank();
        assertThat(locked.body()).contains("TOO_MANY_ATTEMPTS").contains("retryAfterSeconds");

        ResponseEntity<Map> forgot = client.post()
                .uri("/api/v1/auth/forgot-password")
                .body(Map.of("email", "bloqueo@example.com"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        register("reinicio@example.com", "1000000002", "3001112202");

        assertThat(loginStatus("reinicio@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("reinicio@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("reinicio@example.com", "ChangeMe123!")).isEqualTo(HttpStatus.OK);

        assertThat(loginStatus("reinicio@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("reinicio@example.com", "Incorrecta123!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("reinicio@example.com", "ChangeMe123!")).isEqualTo(HttpStatus.OK);
    }

    private void register(String email, String dniNumber, String phone) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Usuario Bloqueo",
                        "email", email,
                        "phone", phone,
                        "password", "ChangeMe123!",
                        "dniType", "CC",
                        "dniNumber", dniNumber,
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpStatusCode loginStatus(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange((request, response) -> response.getStatusCode());
    }

    private Exchange login(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange((request, response) -> new Exchange(
                        response.getStatusCode(),
                        response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private record Exchange(HttpStatusCode status, String retryAfter, String body) {}
}
