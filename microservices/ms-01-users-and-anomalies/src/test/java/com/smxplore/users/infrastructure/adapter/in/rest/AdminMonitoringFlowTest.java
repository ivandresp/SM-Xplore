package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.mongodb.MongoDBContainer;
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
class AdminMonitoringFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

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
        registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/smxplore_reviews");
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
    void adminSeesOverviewAvailabilityAndExportsAudit() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String touristEmail = "monitor.tourist@example.com";
        registerTourist("Turista Monitoreo", touristEmail, "9990001001", "3030001001");
        String providerId =
                registerProvider("Prestadora Monitoreo", "monitor.provider@example.com", "901000999-1", "3030001002");
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);

        String touristToken = login(touristEmail, PASSWORD);
        ResponseEntity<Map> review =
                createReview(touristToken, providerId, "PRESTADORA_SERVICIOS", 4, "Muy buena experiencia");
        assertThat(review.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(updateThreshold(adminToken, "ACCESO_SOSPECHOSO", 2, 120, "ALTA", true)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(loginStatus(touristEmail, "WrongPassword1!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(touristEmail, "WrongPassword1!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(detect(adminToken)).isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> overview = getWithToken(adminToken, "/api/v1/admin/monitoring/overview");
        Map<?, ?> users = (Map<?, ?>) overview.get("users");
        assertThat(number(users, "total")).isGreaterThanOrEqualTo(3);
        assertThat(number(users, "active")).isGreaterThanOrEqualTo(3);
        assertThat(number(users, "tourists")).isGreaterThanOrEqualTo(1);
        assertThat(number(users, "providers")).isGreaterThanOrEqualTo(1);
        assertThat(number(users, "approvedProviders")).isGreaterThanOrEqualTo(1);
        assertThat(number(users, "pendingProviders")).isZero();
        assertThat(number(users, "newUsersLast30Days")).isGreaterThanOrEqualTo(3);

        Map<?, ?> reviews = (Map<?, ?>) overview.get("reviews");
        assertThat(number(reviews, "total")).isEqualTo(1);
        assertThat(number(reviews, "visible")).isEqualTo(1);
        assertThat(number(reviews, "hidden")).isZero();
        assertThat(number(reviews, "reported")).isZero();
        assertThat(reviews.get("averageRating")).isEqualTo(4.0);

        Map<?, ?> anomalies = (Map<?, ?>) overview.get("anomalies");
        assertThat(number(anomalies, "open")).isEqualTo(1);
        assertThat(number(anomalies, "resolved")).isZero();
        assertThat(number(anomalies, "openHigh")).isEqualTo(1);

        Map<?, ?> overviewAvailability = (Map<?, ?>) overview.get("availability");
        assertThat(overviewAvailability.get("available")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) overviewAvailability.get("components"))
                .hasSize(2);

        Map<?, ?> availability = getWithToken(adminToken, "/api/v1/admin/monitoring/availability");
        assertThat(availability.get("available")).isEqualTo(true);
        List<Map<String, Object>> components = (List<Map<String, Object>>) availability.get("components");
        assertThat(components).hasSize(2);
        assertThat(components)
                .allSatisfy(component -> assertThat(component.get("available")).isEqualTo(true));
        assertThat(components)
                .extracting(component -> component.get("name"))
                .containsExactlyInAnyOrder("PostgreSQL", "MongoDB");

        ResponseEntity<String> export = exportAudit(adminToken, "?action=PRESTADORA_APROBADA");
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getContentType()).isNotNull();
        assertThat(export.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(export.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("auditoria-");
        assertThat(export.getBody()).contains("fecha,actor,rol,accion,usuarioAfectado,detalles");
        assertThat(export.getBody()).contains("PRESTADORA_APROBADA");

        ResponseEntity<byte[]> pdf = exportAuditPdf(adminToken, "?action=PRESTADORA_APROBADA");
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType()).isNotNull();
        assertThat(pdf.getHeaders().getContentType().toString()).startsWith("application/pdf");
        assertThat(pdf.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("auditoria-")
                .contains(".pdf\"");
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void nonAdminCannotAccessMonitoring() {
        registerTourist("Turista Monitor Sin Permiso", "monitor.nonadmin@example.com", "9990002002", "3030002002");
        String token = login("monitor.nonadmin@example.com", PASSWORD);

        assertThat(getStatus(token, "/api/v1/admin/monitoring/overview")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getStatus(token, "/api/v1/admin/monitoring/availability")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getStatus(token, "/api/v1/admin/audit-logs/export")).isEqualTo(HttpStatus.FORBIDDEN);
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

    private String registerProvider(String name, String email, String nit, String phone) {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/prestadora")
                .body(Map.of(
                        "fullName", name,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "nit", nit,
                        "businessType", "Agencia de viajes"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(registration.getBody().get("id"));
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

    private HttpStatusCode reviewProvider(String token, String userId, String decision) {
        return client.post()
                .uri("/api/v1/admin/users/" + userId + "/provider-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("decision", decision))
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> createReview(
            String token, String targetId, String targetType, int rating, String comment) {
        return client.post()
                .uri("/api/v1/reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("targetType", targetType, "targetId", targetId, "rating", rating, "comment", comment))
                .retrieve()
                .toEntity(Map.class);
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

    private HttpStatusCode detect(String token) {
        return client.post()
                .uri("/api/v1/admin/anomalies/detect")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }

    private Map<?, ?> getWithToken(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class)
                .getBody();
    }

    private ResponseEntity<String> exportAudit(String token, String query) {
        return client.get()
                .uri("/api/v1/admin/audit-logs/export" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<byte[]> exportAuditPdf(String token, String query) {
        return client.get()
                .uri("/api/v1/admin/audit-logs/export/pdf" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(byte[].class);
    }

    private HttpStatusCode getStatus(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }

    private static long number(Map<?, ?> source, String key) {
        return ((Number) source.get(key)).longValue();
    }

    @BeforeAll
    static void verifyMongoIsReachable() {
        ContainerDiagnostics.verifyMongoReachable(MONGO);
    }

    @AfterAll
    static void dumpMongoLogs() {
        ContainerDiagnostics.dumpMongoLogs(MONGO, "admin-monitoring");
    }
}
