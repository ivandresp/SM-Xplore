package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * MS01-B8: el registro público de guías deja el alta pendiente y sin acceso hasta que una gestora la aprueba; el
 * login distingue esa situación solo después de verificar la contraseña.
 */
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
class GuideRegistrationFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";

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

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void aRegisteredGuideCannotLogInUntilTheGestoraApprovesIt() {
        ResponseEntity<Map> registration = register("Gina Pendiente", "gina.pendiente@example.com", "LIC-100");
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registration.getBody().get("role")).isEqualTo("GUIDE");
        assertThat(profile(registration).get("registrationStatus")).isEqualTo("PENDIENTE");
        String guideId = String.valueOf(registration.getBody().get("id"));

        ResponseEntity<Map> pending = loginAttempt("gina.pendiente@example.com", PASSWORD, HttpStatus.FORBIDDEN);
        assertThat(pending.getBody()).containsEntry("code", "ACCOUNT_PENDING_REVIEW");
        assertThat(String.valueOf(pending.getBody().get("detail"))).contains("revisión");

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(review(adminToken, guideId, "/guide-review", "APROBADO", "Documentación verificada"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + guideId);
        assertThat(((Map<?, ?>) detail.get("profile")).get("registrationStatus"))
                .isEqualTo("APROBADO");

        String guideToken = login("gina.pendiente@example.com", PASSWORD);
        ResponseEntity<Map> me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + guideToken)
                .retrieve()
                .toEntity(Map.class);
        assertThat(me.getBody().get("role")).isEqualTo("GUIDE");
    }

    @Test
    void aPendingGuideWithAWrongPasswordLooksLikeAnyOtherBadCredential() {
        register("Gina Confusa", "gina.confusa@example.com", "LIC-101");

        ResponseEntity<Map> wrongPassword =
                loginAttempt("gina.confusa@example.com", "OtraClave123!", HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).containsEntry("code", "INVALID_CREDENTIALS");
    }

    @Test
    void aBlockedGuideWithTheRightPasswordLooksLikeAnyOtherBadCredential() {
        String guideId = String.valueOf(register("Gina Bloqueada", "gina.bloqueada@example.com", "LIC-102")
                .getBody()
                .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(review(adminToken, guideId, "/guide-review", "APROBADO", null))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "BLOQUEADO");
        body.put("reason", "Incumplimiento de políticas");
        HttpStatusCode blocked = client.patch()
                .uri("/api/v1/admin/users/" + guideId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
        assertThat(blocked).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> response = loginAttempt("gina.bloqueada@example.com", PASSWORD, HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "INVALID_CREDENTIALS");
    }

    @Test
    void aRejectedGuideKeepsTheReasonInTheAuditTrailAndCannotLogIn() {
        String guideId = String.valueOf(register("Gina Rechazada", "gina.rechazada@example.com", "LIC-103")
                .getBody()
                .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(review(adminToken, guideId, "/guide-review", "RECHAZADO", "Licencia no verificable"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> rejected = loginAttempt("gina.rechazada@example.com", PASSWORD, HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).containsEntry("code", "ACCOUNT_REGISTRATION_REJECTED");

        ResponseEntity<Map> audit = client.get()
                .uri("/api/v1/admin/audit-logs?targetUserId=" + guideId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toEntity(Map.class);
        assertThat(String.valueOf(audit.getBody().get("content")))
                .contains("GUIA_RECHAZADA")
                .contains("Licencia no verificable");
    }

    @Test
    void onlyReviewableProfilesAcceptAGuideReview() {
        ResponseEntity<Map> tourist = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Turista Sin Alta",
                        "email", "turista.review@example.com",
                        "phone", "3001234567",
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "1234567890",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        String touristId = String.valueOf(tourist.getBody().get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(review(adminToken, touristId, "/guide-review", "APROBADO", null))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theGuideReviewRequiresAuthentication() {
        HttpStatusCode anonymous = client.post()
                .uri("/api/v1/admin/users/00000000-0000-0000-0000-000000000000/guide-review")
                .body(Map.of("decision", "APROBADO"))
                .exchange((request, response) -> response.getStatusCode());

        assertThat(anonymous).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> register(String fullName, String email, String license) {
        int sequence = SEQUENCE.incrementAndGet();
        return client.post()
                .uri("/api/v1/auth/register/guia")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", String.format("300555%04d", sequence),
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", String.format("1000%06d", sequence),
                        "license", license))
                .retrieve()
                .toEntity(Map.class);
    }

    private ResponseEntity<Map> loginAttempt(String email, String password, HttpStatus expected) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .onStatus(status -> status.value() == expected.value(), (request, response) -> {})
                .toEntity(Map.class);
    }

    private String login(String email, String password) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return String.valueOf(response.getBody().get("accessToken"));
    }

    private HttpStatusCode review(String token, String userId, String path, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/admin/users/" + userId + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private Map<?, ?> get(String token, String uri) {
        ResponseEntity<Map> response = client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static Map<?, ?> profile(ResponseEntity<Map> response) {
        return (Map<?, ?>) response.getBody().get("profile");
    }
}
