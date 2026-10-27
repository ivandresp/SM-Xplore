package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
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
class AnomalyFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/"
                        + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminManagesDetectedAnomaliesAndThresholds() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // La verificación de disponibilidad se cubre en pruebas unitarias; se desactiva para no exigir MongoDB aquí.
        assertThat(updateThreshold(adminToken, "CAIDA_SERVICIO", 2, 60, "CRITICA", false)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(updateThreshold(adminToken, "ACCESO_SOSPECHOSO", 3, 120, "ALTA", true)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String touristEmail = "anomalia.tourist@example.com";
        registerTourist("Turista Anomalía", touristEmail, "8880001001", "3020001001");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(loginStatus(touristEmail, "WrongPassword1!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(detect(adminToken)).isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> page = getWithToken(adminToken, "/api/v1/admin/anomalies?type=ACCESO_SOSPECHOSO&status=DETECTADA");
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).hasSize(1);
        Map<String, Object> anomaly = content.getFirst();
        assertThat(anomaly.get("type")).isEqualTo("ACCESO_SOSPECHOSO");
        assertThat(anomaly.get("severity")).isEqualTo("ALTA");
        assertThat(anomaly.get("status")).isEqualTo("DETECTADA");
        assertThat((String) anomaly.get("description")).contains("3");
        String anomalyId = String.valueOf(anomaly.get("id"));

        Map<?, ?> detail = getWithToken(adminToken, "/api/v1/admin/anomalies/" + anomalyId);
        assertThat(detail.get("typeLabel")).isEqualTo("Accesos sospechosos");

        ResponseEntity<Map> classified =
                patch(adminToken, "/api/v1/admin/anomalies/" + anomalyId + "/severity", Map.of("severity", "CRITICA"));
        assertThat(classified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(classified.getBody().get("severity")).isEqualTo("CRITICA");

        ResponseEntity<Map> resolved = patch(
                adminToken,
                "/api/v1/admin/anomalies/" + anomalyId + "/resolve",
                Map.of("note", "Revisado con el equipo de seguridad"));
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().get("status")).isEqualTo("RESUELTA");
        assertThat(resolved.getBody().get("resolvedAt")).isNotNull();
        assertThat(resolved.getBody().get("resolutionNote")).isEqualTo("Revisado con el equipo de seguridad");

        ResponseEntity<Map> resolvedAgain =
                patch(adminToken, "/api/v1/admin/anomalies/" + anomalyId + "/resolve", Map.of("note", "Otra vez"));
        assertThat(resolvedAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resolvedAgain.getBody()).containsEntry("code", "ANOMALY_ALREADY_RESOLVED");

        assertThat(detect(adminToken)).isEqualTo(HttpStatus.NO_CONTENT);
        Map<?, ?> after = getWithToken(adminToken, "/api/v1/admin/anomalies?type=ACCESO_SOSPECHOSO");
        assertThat((List<Map<String, Object>>) after.get("content")).hasSize(1);

        ResponseEntity<Map> changed = updateThreshold(adminToken, "PICO_SOLICITUDES", 10, 30, "ALTA", true);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("thresholdValue")).isEqualTo(10);

        List<Map<String, Object>> thresholds = thresholds(adminToken);
        assertThat(thresholds)
                .extracting(threshold -> threshold.get("type"))
                .containsExactlyInAnyOrder("ACCESO_SOSPECHOSO", "CAIDA_SERVICIO", "PICO_SOLICITUDES");
        assertThat(thresholds)
                .filteredOn(threshold -> "PICO_SOLICITUDES".equals(threshold.get("type")))
                .singleElement()
                .satisfies(
                        threshold -> assertThat(threshold.get("thresholdValue")).isEqualTo(10));

        Map<?, ?> audit = getWithToken(adminToken, "/api/v1/admin/audit-logs?action=ANOMALIA_RESUELTA");
        assertThat((List<Map<String, Object>>) audit.get("content")).isNotEmpty();

        assertThat(getStatus(adminToken, "/api/v1/admin/anomalies?type=DESCONOCIDO"))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdminCannotAccessAnomalies() {
        registerTourist("Turista Sin Permiso", "anomalia.nonadmin@example.com", "8880002002", "3020002002");
        String token = login("anomalia.nonadmin@example.com", PASSWORD);

        assertThat(getStatus(token, "/api/v1/admin/anomalies")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getStatus(token, "/api/v1/admin/anomaly-thresholds")).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void registerTourist(String name, String email, String dni, String phone) {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", name,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", dni,
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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

    private HttpStatusCode detect(String token) {
        return client.post()
                .uri("/api/v1/admin/anomalies/detect")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> updateThreshold(
            String token, String type, int thresholdValue, int windowSeconds, String severity, boolean enabled) {
        return client.put()
                .uri("/api/v1/admin/anomaly-thresholds/" + type)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of(
                        "thresholdValue", thresholdValue,
                        "windowSeconds", windowSeconds,
                        "severity", severity,
                        "enabled", enabled))
                .retrieve()
                .toEntity(Map.class);
    }

    private List<Map<String, Object>> thresholds(String token) {
        return client.get()
                .uri("/api/v1/admin/anomaly-thresholds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map> patch(String token, String uri, Map<String, Object> body) {
        return client.patch()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> {})
                .toEntity(Map.class);
    }

    private Map<?, ?> getWithToken(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class)
                .getBody();
    }

    private HttpStatusCode getStatus(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }
}
