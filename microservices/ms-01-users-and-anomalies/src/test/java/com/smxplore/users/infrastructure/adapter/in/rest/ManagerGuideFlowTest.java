package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * Flujo de registro y aprobación de guías y gestoras de destinos: auto-registro público en PENDIENTE, listado y revisión
 * por parte de una gestora, y aprobación de gestoras por parte de un administrador.
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
class ManagerGuideFlowTest {

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

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void publicRegistrationCreatesPendingProfilesAndRejectsDuplicates() {
        Map<?, ?> guide = registerGuide("Guía Pública", "guia.publica@example.com", "3105550001", "LIC-501");
        assertThat(guide.get("role")).isEqualTo("GUIDE");
        assertThat(guide.get("status")).isEqualTo("ACTIVO");
        assertThat(registrationStatus(guide)).isEqualTo("PENDIENTE");

        Map<?, ?> gestora =
                registerGestora("Gestora Pública", "gestora.publica@example.com", "3105550002", "900555501-1");
        assertThat(gestora.get("role")).isEqualTo("DESTINATION_MANAGER");
        assertThat(gestora.get("status")).isEqualTo("ACTIVO");
        assertThat(registrationStatus(gestora)).isEqualTo("PENDIENTE");

        // La aprobación es independiente del acceso: una cuenta pendiente puede iniciar sesión.
        assertThat(loginStatus("guia.publica@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);
        assertThat(loginStatus("gestora.publica@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);

        assertThat(registerStatus(Map.of(
                        "fullName", "Guía Duplicada",
                        "email", "guia.publica@example.com",
                        "phone", "3105550003",
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "1000005010",
                        "license", "LIC-501",
                        "startOperationDate", "2024-01-01")))
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(registerStatus(Map.of(
                        "fullName", "Guía Inválida",
                        "email", "guia.invalida@example.com",
                        "phone", "3105550004",
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "1000005011",
                        "license", "  ",
                        "startOperationDate", "2024-01-01")))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminListsAndApprovesPendingDestinationManagers() {
        String gestoraId = String.valueOf(
                registerGestora("Gestora Admin", "gestora.admin@example.com", "3105550011", "900555511-1")
                        .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> pending =
                get(adminToken, "/api/v1/admin/users?role=DESTINATION_MANAGER&registrationStatus=PENDIENTE");
        assertThat(users(pending)).anySatisfy(user -> {
            assertThat(user.get("id")).isEqualTo(gestoraId);
            assertThat(user.get("registrationStatus")).isEqualTo("PENDIENTE");
        });

        assertThat(reviewGestora(adminToken, gestoraId, "APROBADO", "Cámara de comercio validada"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + gestoraId);
        assertThat(registrationStatus(detail)).isEqualTo("APROBADO");

        Map<?, ?> audit = get(adminToken, "/api/v1/admin/audit-logs?targetUserId=" + gestoraId);
        assertThat(entries(audit)).anySatisfy(entry -> {
            assertThat(entry.get("action")).isEqualTo("GESTORA_APROBADA");
            assertThat(entry.get("actorEmail")).isEqualTo(ADMIN_EMAIL);
            assertThat(entry.get("actorRole")).isEqualTo("ADMIN");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void destinationManagerListsAndApprovesPendingGuides() {
        String guideId =
                String.valueOf(registerGuide("Guía Revisada", "guia.revisada@example.com", "3105550021", "LIC-521")
                        .get("id"));
        String gestoraId = String.valueOf(
                registerGestora("Gestora Revisora", "gestora.revisora@example.com", "3105550022", "900555521-1")
                        .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewGestora(adminToken, gestoraId, "APROBADO", null)).isEqualTo(HttpStatus.NO_CONTENT);
        String gestoraToken = login("gestora.revisora@example.com", PASSWORD);

        Map<?, ?> pending = get(gestoraToken, "/api/v1/manager/guides?status=PENDIENTE");
        List<Map<String, Object>> pendingGuides = (List<Map<String, Object>>) pending.get("content");
        assertThat(pendingGuides).anySatisfy(guide -> {
            assertThat(guide.get("id")).isEqualTo(guideId);
            assertThat(guide.get("registrationStatus")).isEqualTo("PENDIENTE");
            assertThat(guide.get("license")).isEqualTo("LIC-521");
        });

        assertThat(reviewGuide(gestoraToken, guideId, "APROBADO", "Licencia vigente"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + guideId);
        assertThat(registrationStatus(detail)).isEqualTo("APROBADO");

        Map<?, ?> approved = get(gestoraToken, "/api/v1/manager/guides?status=APROBADO");
        assertThat((List<Map<String, Object>>) approved.get("content"))
                .anySatisfy(guide -> assertThat(guide.get("id")).isEqualTo(guideId));

        assertThat(reviewGuide(gestoraToken, guideId, "APROBADO", null)).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> audit = get(adminToken, "/api/v1/admin/audit-logs?targetUserId=" + guideId);
        assertThat(entries(audit)).anySatisfy(entry -> {
            assertThat(entry.get("action")).isEqualTo("GUIA_APROBADA");
            assertThat(entry.get("actorEmail")).isEqualTo("gestora.revisora@example.com");
            assertThat(entry.get("actorRole")).isEqualTo("DESTINATION_MANAGER");
            Map<String, Object> details = (Map<String, Object>) entry.get("details");
            assertThat(details).containsEntry("decision", "APROBADO").containsEntry("motivo", "Licencia vigente");
        });

        // Una guía aprobada ya es visible en el perfil público.
        assertThat(anonymousStatus("/api/v1/public/users/" + guideId)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void administratorCanAlsoReviewGuides() {
        String guideId = String.valueOf(registerGuide("Guía Admin", "guia.admin@example.com", "3105550031", "LIC-531")
                .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(reviewGuide(adminToken, guideId, "RECHAZADO", "Licencia no verificable"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + guideId);
        assertThat(registrationStatus(detail)).isEqualTo("RECHAZADO");
    }

    @Test
    void authorizationIsEnforcedForTheNewEndpoints() {
        String guideId = String.valueOf(
                registerGuide("Guía Sin Permiso", "guia.sin.permiso@example.com", "3105550041", "LIC-541")
                        .get("id"));
        String gestoraId = String.valueOf(
                registerGestora("Gestora Sin Permiso", "gestora.sin.permiso@example.com", "3105550042", "900555541-1")
                        .get("id"));
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewGestora(adminToken, gestoraId, "APROBADO", null)).isEqualTo(HttpStatus.NO_CONTENT);
        String guideToken = login("guia.sin.permiso@example.com", PASSWORD);
        String gestoraToken = login("gestora.sin.permiso@example.com", PASSWORD);

        assertThat(status(guideToken, "/api/v1/manager/guides")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reviewGuide(guideToken, guideId, "APROBADO", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reviewGestora(gestoraToken, gestoraId, "APROBADO", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymousStatus("/api/v1/manager/guides")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Map<?, ?> registerGuide(String fullName, String email, String phone, String license) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", PASSWORD);
        body.put("dniType", "CC");
        body.put("dniNumber", "100000" + phone.substring(phone.length() - 4));
        body.put("license", license);
        body.put("startOperationDate", LocalDate.parse("2024-01-01").toString());
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/guia")
                .body(body)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<?, ?> registerGestora(String fullName, String email, String phone, String nit) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/gestora")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "nit", nit,
                        "startOperationDate", "2024-01-01"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpStatusCode registerStatus(Map<String, Object> body) {
        return client.post()
                .uri("/api/v1/auth/register/guia")
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
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

    private HttpStatusCode loginStatus(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
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

    private HttpStatusCode status(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode anonymousStatus(String uri) {
        return client.get().uri(uri).exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode reviewGuide(String token, String guideId, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/manager/guides/" + guideId + "/review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode reviewGestora(String token, String gestoraId, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/admin/users/" + gestoraId + "/gestora-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private static Object registrationStatus(Map<?, ?> user) {
        return ((Map<?, ?>) user.get("profile")).get("registrationStatus");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> users(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }
}
