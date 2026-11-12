package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
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
 * El registro público de gestoras de destinos deja el alta pendiente y sin acceso hasta que un administrador la
 * aprueba; la cola de altas la consulta la gestora o un administrador, pero solo un administrador decide sobre otra
 * gestora.
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
class GestoraRegistrationFlowTest {

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
    void aRegisteredGestoraCannotLogInUntilAnAdminApprovesIt() {
        ResponseEntity<Map> registration = register("Gina Pendiente", "gina.gestora.pendiente@example.com");
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registration.getBody().get("role")).isEqualTo("DESTINATION_MANAGER");
        assertThat(profile(registration).get("registrationStatus")).isEqualTo("PENDIENTE");
        String gestoraId = String.valueOf(registration.getBody().get("id"));

        ResponseEntity<Map> pending =
                loginAttempt("gina.gestora.pendiente@example.com", PASSWORD, HttpStatus.FORBIDDEN);
        assertThat(pending.getBody()).containsEntry("code", "ACCOUNT_PENDING_REVIEW");

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(pendingGestoras(adminToken).stream()
                        .map(entry -> String.valueOf(((Map<?, ?>) entry).get("id")))
                        .toList())
                .contains(gestoraId);

        assertThat(review(adminToken, gestoraId, "APROBADO", "Documentación verificada"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + gestoraId);
        assertThat(((Map<?, ?>) detail.get("profile")).get("registrationStatus"))
                .isEqualTo("APROBADO");

        String gestoraToken = login("gina.gestora.pendiente@example.com", PASSWORD);
        ResponseEntity<Map> me = client.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestoraToken)
                .retrieve()
                .toEntity(Map.class);
        assertThat(me.getBody().get("role")).isEqualTo("DESTINATION_MANAGER");
    }

    @Test
    void aRejectedGestoraCannotLogIn() {
        String gestoraId = String.valueOf(register("Gina Rechazada", "gina.gestora.rechazada@example.com")
                .getBody()
                .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(review(adminToken, gestoraId, "RECHAZADO", "NIT no verificable"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> rejected =
                loginAttempt("gina.gestora.rechazada@example.com", PASSWORD, HttpStatus.FORBIDDEN);
        assertThat(rejected.getBody()).containsEntry("code", "ACCOUNT_REGISTRATION_REJECTED");
    }

    @Test
    void theRegistrationQueueRequiresAuthentication() {
        HttpStatusCode anonymous =
                client.get().uri("/api/v1/registrations").exchange((request, response) -> response.getStatusCode());

        assertThat(anonymous).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> register(String fullName, String email) {
        int sequence = SEQUENCE.incrementAndGet();
        return client.post()
                .uri("/api/v1/auth/register/gestora")
                .body(Map.of(
                        "fullName",
                        fullName,
                        "email",
                        email,
                        "phone",
                        String.format("300777%04d", sequence),
                        "password",
                        PASSWORD,
                        "nit",
                        String.format("900000%03d-9", sequence),
                        "startOperationDate",
                        "2024-01-15"))
                .retrieve()
                .toEntity(Map.class);
    }

    private List<?> pendingGestoras(String token) {
        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/registrations?role=DESTINATION_MANAGER&status=PENDIENTE&size=50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<?>) response.getBody().get("content");
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

    private HttpStatusCode review(String token, String userId, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/registrations/" + userId + "/review")
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
