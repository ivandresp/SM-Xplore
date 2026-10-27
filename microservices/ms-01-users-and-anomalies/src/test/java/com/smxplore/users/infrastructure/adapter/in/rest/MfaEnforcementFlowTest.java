package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.port.out.TotpAuthenticator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
            "app.bootstrap-admin.full-name=Admin Test",
            "app.security.mfa.required-for-admins=true",
            "app.security.lockout.enabled=false",
            "app.security.rate-limit.requests-per-minute=120"
        })
@Testcontainers
@EnabledIfDockerAvailable
class MfaEnforcementFlowTest {

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

    @Autowired
    private TotpAuthenticator totp;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void blocksAdminEndpointsUntilMfaIsEnrolled() {
        Map<String, Object> session = login("admin@test.local", "ChangeMe123!");
        assertThat(session.get("mfaRequired")).isEqualTo(false);
        String pendingToken = (String) session.get("accessToken");

        Exchange blocked = get("/api/v1/admin/users", pendingToken);
        assertThat(blocked.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.body()).contains("MFA_ENROLLMENT_REQUIRED");

        assertThat(get("/api/v1/users/me", pendingToken).status()).isEqualTo(HttpStatus.OK);

        Map<String, Object> enrollment = post("/api/v1/users/me/mfa/enroll", Map.of(), pendingToken);
        String secret = (String) enrollment.get("secret");
        assertThat(postStatus(
                        "/api/v1/users/me/mfa/confirm",
                        Map.of("code", totp.currentCode(secret, Instant.now())),
                        pendingToken))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> challenge = login("admin@test.local", "ChangeMe123!");
        assertThat(challenge.get("mfaRequired")).isEqualTo(true);

        String code = totp.currentCode(secret, Instant.now().plusSeconds(30));
        Map<String, Object> verified = post(
                "/api/v1/auth/mfa/verify",
                Map.of("challengeToken", (String) challenge.get("mfaChallengeToken"), "code", code),
                null);
        String adminToken = (String) verified.get("accessToken");
        assertThat(adminToken).isNotBlank();

        assertThat(get("/api/v1/admin/users", adminToken).status()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String email, String password) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Exchange get(String uri, String token) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> new Exchange(
                        response.getStatusCode(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String uri, Map<String, Object> body, String token) {
        RestClient.RequestBodySpec spec = client.post().uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        ResponseEntity<Map> response = spec.body(body).retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    private HttpStatusCode postStatus(String uri, Map<String, Object> body, String token) {
        return client.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private record Exchange(HttpStatusCode status, String body) {}
}
