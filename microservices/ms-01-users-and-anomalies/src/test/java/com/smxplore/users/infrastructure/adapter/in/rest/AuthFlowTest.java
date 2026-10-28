package com.smxplore.users.infrastructure.adapter.in.rest;

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
        properties = {
            "app.bootstrap-admin.enabled=true",
            "app.bootstrap-admin.email=admin@test.local",
            "app.bootstrap-admin.password=ChangeMe123!",
            "app.bootstrap-admin.full-name=Admin Test"
        })
@Testcontainers
@EnabledIfDockerAvailable
class AuthFlowTest {

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
    void registersLogsInAndReadsCurrentUser() {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Ada Turista",
                        "email", "ada@example.com",
                        "phone", "3001234567",
                        "password", "ChangeMe123!",
                        "dniType", "CC",
                        "dniNumber", "1234567890",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);

        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registration.getBody().get("role")).isEqualTo("TURISTA");

        ResponseEntity<Map> login = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", "ada@example.com", "password", "ChangeMe123!"))
                .retrieve()
                .toEntity(Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = String.valueOf(login.getBody().get("accessToken"));
        assertThat(accessToken).isNotBlank();

        ResponseEntity<Map> me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(Map.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo("ada@example.com");
    }

    @Test
    void logoutRevokesTheIssuedTokens() {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Luisa Logout",
                        "email", "logout@example.com",
                        "phone", "3009998877",
                        "password", "ChangeMe123!",
                        "dniType", "CC",
                        "dniNumber", "9998887776",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> login = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", "logout@example.com", "password", "ChangeMe123!"))
                .retrieve()
                .toEntity(Map.class);
        String accessToken = String.valueOf(login.getBody().get("accessToken"));

        ResponseEntity<Map> me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> logout = client.post()
                .uri("/api/v1/users/me/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpStatusCode afterLogout = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange((request, response) -> response.getStatusCode());
        assertThat(afterLogout).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> secondLogin = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", "logout@example.com", "password", "ChangeMe123!"))
                .retrieve()
                .toEntity(Map.class);
        String freshToken = String.valueOf(secondLogin.getBody().get("accessToken"));

        ResponseEntity<Map> afterSecondLogin = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken)
                .retrieve()
                .toEntity(Map.class);
        assertThat(afterSecondLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bootstrappedAdminCanLoginAndJwksIsPublic() {
        ResponseEntity<Map> login = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", "admin@test.local", "password", "ChangeMe123!"))
                .retrieve()
                .toEntity(Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = String.valueOf(login.getBody().get("accessToken"));

        ResponseEntity<Map> me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(Map.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("role")).isEqualTo("ADMIN");

        ResponseEntity<Map> jwks =
                client.get().uri("/.well-known/jwks.json").retrieve().toEntity(Map.class);

        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody().get("keys")).isNotNull();
    }
}
