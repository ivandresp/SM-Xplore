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
 * MS01-B8: cola de solicitudes de alta para la gestora.
 *
 * <p>La gestora no entra en `/admin/users` (ese CRUD sigue siendo solo de ADMIN); usa `/api/v1/registrations`, que
 * expone lo mínimo para revisar: quién solicita, su licencia o NIT y el estado del alta.
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
class RegistrationQueueFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
    void aGestoraListsPendingGuidesAndApprovesOne() {
        String guideId = registerGuide("Gina Cola", "gina.cola@example.com", "LIC-COLA-1");
        registerProvider("Prestadora Cola", "prestadora.cola@example.com", "900111222-3");
        String gestoraToken = gestora("gestora.cola@example.com");

        Map<?, ?> pendingGuides = registrations(gestoraToken, "?role=GUIDE&status=PENDIENTE");
        assertThat(entries(pendingGuides)).singleElement().satisfies(registration -> {
            assertThat(registration.get("id")).isEqualTo(guideId);
            assertThat(registration.get("role")).isEqualTo("GUIDE");
            assertThat(registration.get("fullName")).isEqualTo("Gina Cola");
            assertThat(registration.get("email")).isEqualTo("gina.cola@example.com");
            assertThat(registration.get("license")).isEqualTo("LIC-COLA-1");
            assertThat(registration.get("nit")).isNull();
            assertThat(registration.get("registrationStatus")).isEqualTo("PENDIENTE");
            assertThat(registration.get("signedUpAt")).isNotNull();
        });

        assertThat(reviewStatus(gestoraToken, guideId, "APROBADO", "Documentación verificada"))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(loginStatus("gina.cola@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);

        assertThat(entries(registrations(gestoraToken, "?role=GUIDE&status=PENDIENTE")))
                .noneSatisfy(registration -> assertThat(registration.get("id")).isEqualTo(guideId));
        assertThat(entries(registrations(gestoraToken, "?role=GUIDE&status=APROBADO")))
                .anySatisfy(registration -> assertThat(registration.get("id")).isEqualTo(guideId));
    }

    @Test
    void theSameScreenServesProviders() {
        String providerId = registerProvider("Prestadora Cola Dos", "prestadora.cola2@example.com", "900111333-4");
        String gestoraToken = gestora("gestora.cola2@example.com");

        Map<?, ?> pendingProviders = registrations(gestoraToken, "?role=SERVICE_PROVIDER&status=PENDIENTE");
        assertThat(entries(pendingProviders)).singleElement().satisfies(registration -> {
            assertThat(registration.get("id")).isEqualTo(providerId);
            assertThat(registration.get("role")).isEqualTo("SERVICE_PROVIDER");
            assertThat(registration.get("nit")).isEqualTo("900111333-4");
            assertThat(registration.get("license")).isNull();
        });

        assertThat(reviewStatus(gestoraToken, providerId, "RECHAZADO", "NIT no verificable"))
                .isEqualTo(HttpStatus.NO_CONTENT);
        // La prestadora rechazada conserva el acceso que ya tenía el flujo aprobado: el estado del alta solo
        // controla la exposición pública y la publicación, no el inicio de sesión. El guía, en cambio, no entra
        // hasta que se aprueba (MS01-B8).
        assertThat(loginStatus("prestadora.cola2@example.com", PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anAdminCanUseTheSameQueue() {
        String guideId = registerGuide("Gina Admin", "gina.admin.cola@example.com", "LIC-COLA-2");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(entries(registrations(adminToken, "?role=GUIDE&status=PENDIENTE")))
                .anySatisfy(registration -> assertThat(registration.get("id")).isEqualTo(guideId));
        assertThat(reviewStatus(adminToken, guideId, "APROBADO", null)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aTouristCannotSeeOrReviewTheQueue() {
        String guideId = registerGuide("Gina Ajena", "gina.ajena@example.com", "LIC-COLA-3");
        registerTourist("Turista Curioso", "turista.cola@example.com");
        String touristToken = login("turista.cola@example.com", PASSWORD);

        assertThat(status(touristToken, "/api/v1/registrations?role=GUIDE")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reviewStatus(touristToken, guideId, "APROBADO", null)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reviewingSomethingThatIsNotARequestReturnsNotFound() {
        String touristId = registerTourist("Turista No Revisable", "turista.no-revisable@example.com");
        String gestoraToken = gestora("gestora.cola3@example.com");

        assertThat(reviewStatus(gestoraToken, touristId, "APROBADO", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reviewStatus(gestoraToken, "00000000-0000-0000-0000-000000000000", "APROBADO", null))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theQueueRejectsAnUnknownRoleAndOversizedPages() {
        String gestoraToken = gestora("gestora.cola4@example.com");

        assertThat(status(gestoraToken, "/api/v1/registrations?role=TOURIST")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(gestoraToken, "/api/v1/registrations?role=GUIDE&size=500"))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(gestoraToken, "/api/v1/registrations?role=GUIDE&status=INVENTADO"))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String gestora(String email) {
        String touristId = registerTourist("Gestora Destinos", email);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        ResponseEntity<Map> changed = client.patch()
                .uri("/api/v1/admin/users/" + touristId + "/role")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .body(Map.of("role", "DESTINATION_MANAGER", "nit", "901" + Math.abs(email.hashCode())))
                .retrieve()
                .toEntity(Map.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("role")).isEqualTo("DESTINATION_MANAGER");
        return login(email, PASSWORD);
    }

    private Map<?, ?> registrations(String token, String query) {
        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/registrations" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatusCode reviewStatus(String token, String id, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("reason", reason);
        }
        return client.post()
                .uri("/api/v1/registrations/" + id + "/review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode status(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((request, response) -> response.getStatusCode());
    }

    private String registerGuide(String fullName, String email, String license) {
        int sequence = SEQUENCE.incrementAndGet();
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/guia")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", String.format("300666%04d", sequence),
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", String.format("2000%06d", sequence),
                        "license", license))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private String registerProvider(String fullName, String email, String nit) {
        int sequence = SEQUENCE.incrementAndGet();
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/prestadora")
                .body(Map.of(
                        "fullName", fullName,
                        "email", email,
                        "phone", String.format("300777%04d", sequence),
                        "password", PASSWORD,
                        "nit", nit,
                        "businessType", "Agencia de viajes"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private String registerTourist(String fullName, String email) {
        int sequence = SEQUENCE.incrementAndGet();
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName",
                        fullName,
                        "email",
                        email,
                        "phone",
                        String.format("300888%04d", sequence),
                        "password",
                        PASSWORD,
                        "dniType",
                        "CC",
                        "dniNumber",
                        String.format("3000%06d", sequence),
                        "preferredLanguage",
                        "es",
                        "nationality",
                        "Colombia"))
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

    private HttpStatusCode loginStatus(String email, String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange((request, response) -> response.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }
}
