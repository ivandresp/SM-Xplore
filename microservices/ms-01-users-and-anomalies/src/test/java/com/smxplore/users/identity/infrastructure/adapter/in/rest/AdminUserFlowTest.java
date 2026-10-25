package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

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
class AdminUserFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";

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
    void adminSearchesUsersByNameIdentificationAndFilters() {
        String touristId = registerTourist("Ada Lovelace", "ada.list@example.com", "5550001111", "3005550001");
        String providerId = registerProvider("Guías del Caribe", "guias.list@example.com", "900111222-3", "3005550002");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> byName = get(adminToken, "/api/v1/admin/users?q=lovelace");
        assertThat(users(byName)).singleElement().satisfies(user -> {
            assertThat(user.get("id")).isEqualTo(touristId);
            assertThat(user.get("role")).isEqualTo("TURISTA");
        });

        Map<?, ?> byDni = get(adminToken, "/api/v1/admin/users?q=5550001111");
        assertThat(users(byDni))
                .singleElement()
                .satisfies(user -> assertThat(user.get("id")).isEqualTo(touristId));

        Map<?, ?> byNit = get(adminToken, "/api/v1/admin/users?q=900111222-3");
        assertThat(users(byNit)).singleElement().satisfies(user -> {
            assertThat(user.get("id")).isEqualTo(providerId);
            assertThat(user.get("providerStatus")).isEqualTo("PENDIENTE");
        });

        Map<?, ?> byRole = get(adminToken, "/api/v1/admin/users?role=PRESTADORA_SERVICIOS");
        assertThat(users(byRole))
                .allSatisfy(user -> assertThat(user.get("role")).isEqualTo("PRESTADORA_SERVICIOS"));

        Map<?, ?> detail = get(adminToken, "/api/v1/admin/users/" + providerId);
        assertThat(detail.get("status")).isEqualTo("ACTIVO");
        assertThat(detail.get("profile")).isNotNull();
    }

    @Test
    void adminListingSupportsPaginationAndRejectsInvalidSizes() {
        for (int index = 0; index < 3; index++) {
            registerTourist(
                    "Turista " + index,
                    "turista.pagina" + index + "@example.com",
                    "55500033" + index,
                    "30055503" + index);
        }
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<?, ?> firstPage = get(adminToken, "/api/v1/admin/users?page=0&size=2&sortBy=fullName&sortOrder=ASC");
        assertThat(firstPage.get("size")).isEqualTo(2);
        assertThat((Integer) firstPage.get("totalElements")).isGreaterThanOrEqualTo(4);

        HttpStatusCode invalidSize = status(adminToken, "/api/v1/admin/users?size=500");
        assertThat(invalidSize).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpStatusCode invalidSort = status(adminToken, "/api/v1/admin/users?sortBy=password");
        assertThat(invalidSort).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdminUsersReceiveForbiddenProblemDetails() {
        registerTourist("Ana Novata", "ana.nonadmin@example.com", "5550002222", "3005550222");
        String touristToken = login("ana.nonadmin@example.com", PASSWORD);

        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + touristToken)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value(), (request, result) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void anonymousRequestsReceiveUnauthorizedProblemDetails() {
        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/admin/users")
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(), (request, result) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
    }

    @Test
    void returnsNotFoundForAnUnknownUser() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpStatusCode status = status(adminToken, "/api/v1/admin/users/00000000-0000-0000-0000-000000000000");

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blocksSuspendsAndReactivatesAnAccount() {
        String userId = registerTourist("Bruno Bloqueado", "bruno.block@example.com", "6660001111", "3006660001");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(loginStatus("bruno.block@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);

        HttpStatusCode blocked = changeStatus(adminToken, userId, "BLOQUEADO", "Incumplimiento de políticas");
        assertThat(blocked).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(loginStatus("bruno.block@example.com", PASSWORD)).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(changeStatus(adminToken, userId, "SUSPENDIDO", null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(changeStatus(adminToken, userId, "ACTIVO", null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(loginStatus("bruno.block@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsRedundantTransitionsAndTheDeletionStatus() {
        String userId = registerTourist("Carla Transición", "carla.status@example.com", "6660002222", "3006660002");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(changeStatus(adminToken, userId, "BLOQUEADO", null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(changeStatus(adminToken, userId, "BLOQUEADO", null)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changeStatus(adminToken, userId, "ELIMINADO", null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminCannotChangeOwnStatus() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Map<?, ?> me = get(adminToken, "/api/v1/users/me");
        String adminId = String.valueOf(me.get("id"));

        HttpStatusCode response = changeStatus(adminToken, adminId, "BLOQUEADO", null);

        assertThat(response).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesAnAuditEntryForEachStatusChange() {
        String userId = registerTourist("Diego Auditoría", "diego.audit@example.com", "6660003333", "3006660003");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(changeStatus(adminToken, userId, "SUSPENDIDO", "Revisión de seguridad"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> page = get(adminToken, "/api/v1/admin/audit-logs?targetUserId=" + userId);
        List<Map<String, Object>> entries = entries(page);

        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.get("action")).isEqualTo("ESTADO_CAMBIADO");
            assertThat(entry.get("actorEmail")).isEqualTo(ADMIN_EMAIL);
            assertThat(entry.get("actorRole")).isEqualTo("ADMIN");
            Map<String, Object> details = (Map<String, Object>) entry.get("details");
            assertThat(details)
                    .containsEntry("estadoNuevo", "SUSPENDIDO")
                    .containsEntry("motivo", "Revisión de seguridad");
        });
    }

    @Test
    void approvesAndRejectsProviderRegistrations() {
        String approvedId =
                registerProvider("Prestadora Aprobada", "aprobada@example.com", "900222333-4", "3007770001");
        String rejectedId =
                registerProvider("Prestadora Rechazada", "rechazada@example.com", "900222444-5", "3007770002");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(reviewProvider(adminToken, approvedId, "APROBADO", "Documentación verificada"))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<?, ?> approved = get(adminToken, "/api/v1/admin/users/" + approvedId);
        assertThat(registrationStatus(approved)).isEqualTo("APROBADO");

        assertThat(reviewProvider(adminToken, rejectedId, "RECHAZADO", "NIT no verificable"))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<?, ?> rejected = get(adminToken, "/api/v1/admin/users/" + rejectedId);
        assertThat(registrationStatus(rejected)).isEqualTo("RECHAZADO");

        assertThat(reviewProvider(adminToken, approvedId, "APROBADO", null)).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> audit = get(adminToken, "/api/v1/admin/audit-logs?targetUserId=" + approvedId);
        assertThat(entries(audit))
                .anySatisfy(entry -> assertThat(entry.get("action")).isEqualTo("PRESTADORA_APROBADA"));
    }

    @Test
    void onlyProvidersCanBeReviewed() {
        String touristId =
                registerTourist("Elena No Prestadora", "elena.review@example.com", "7770001111", "3007770011");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(reviewProvider(adminToken, touristId, "APROBADO", null)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reviewProvider(adminToken, touristId, "PENDIENTE", null)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String registerTourist(String fullName, String email, String dniNumber, String phone) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", dniNumber,
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private String registerProvider(String fullName, String email, String nit, String phone) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/prestadora")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "nit", nit,
                        "businessType", "Agencia de viajes"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
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

    private HttpStatusCode loginStatus(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode changeStatus(String token, String userId, String status, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.patch()
                .uri("/api/v1/admin/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode reviewProvider(String token, String userId, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/admin/users/" + userId + "/provider-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private static Object registrationStatus(Map<?, ?> detail) {
        return ((Map<?, ?>) detail.get("profile")).get("registrationStatus");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> users(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }
}
