package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class ChangeOwnPasswordTest {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String NEW_PASSWORD = "NewChangeMe456!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void changesThePasswordAndInvalidatesThePreviousOne() {
        registerTourist("clave@example.com");
        String token = login("clave@example.com", INITIAL_PASSWORD);

        HttpStatusCode wrongCurrent = changePassword(token, "NotMyPassword123!", NEW_PASSWORD);
        assertThat(wrongCurrent).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpStatusCode changed = changePassword(token, INITIAL_PASSWORD, NEW_PASSWORD);
        assertThat(changed).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginStatus("clave@example.com", INITIAL_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("clave@example.com", NEW_PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsANewPasswordThatViolatesThePolicy() {
        registerTourist("corta@example.com");
        String token = login("corta@example.com", INITIAL_PASSWORD);

        HttpStatusCode status = changePassword(token, INITIAL_PASSWORD, "short");

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requiresAuthentication() {
        HttpStatusCode status = client.put()
                .uri("/api/v1/users/me/password")
                .body(Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void registerTourist(String email) {
        ResponseEntity<Void> registration = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Ada Turista",
                        "email", email,
                        "phone", "3001234567",
                        "password", INITIAL_PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "1234567890",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toBodilessEntity();
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String login(String email, String password) {
        Map<?, ?> body = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .body(Map.class);
        return String.valueOf(body.get("accessToken"));
    }

    private HttpStatusCode loginStatus(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode changePassword(String token, String currentPassword, String newPassword) {
        return client.put()
                .uri("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("currentPassword", currentPassword, "newPassword", newPassword))
                .exchange((request, response) -> response.getStatusCode());
    }
}
