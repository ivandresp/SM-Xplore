package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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
class PublicProfileTest {

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
    void doesNotExposeIdentityDocumentsOfGuides() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String guideId = createGuide(adminToken, "guia.publico@example.com", "5550009999", "LIC-PUB");

        ResponseEntity<Map> response = publicProfile(guideId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> profile = profile(response);
        assertThat(profile.get("license")).isEqualTo("LIC-PUB");
        assertThat(profile).doesNotContainKeys("dniType", "dniNumber");
    }

    @Test
    void onlyApprovedProvidersAreVisible() {
        String providerId =
                registerProvider("Prestadora Pública", "prestadora.publica@example.com", "903555666-7", "3008880001");
        assertThat(publicStatus(providerId)).isEqualTo(HttpStatus.NOT_FOUND);

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> response = publicProfile(providerId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> profile = profile(response);
        assertThat(profile.get("nit")).isEqualTo("903555666-7");
        assertThat(profile.get("websiteUrl")).isEqualTo("https://prestadora.example.com");
        assertThat(profile).doesNotContainKeys("dniType", "dniNumber");
    }

    @Test
    void updatesAndClearsTheOptionalProviderWebsite() {
        registerProvider("Prestadora Web", "prestadora.web@example.com", "903555888-9", "3008880004");
        String token = login("prestadora.web@example.com", PASSWORD);

        Map<?, ?> updated = updateWebsite(token, "https://nueva.example.com");
        assertThat(profile(updated).get("websiteUrl")).isEqualTo("https://nueva.example.com");

        Map<?, ?> cleared = updateWebsite(token, "");
        assertThat(profile(cleared).get("websiteUrl")).isNull();
    }

    @Test
    void hidesNonPublicAndBlockedAccounts() {
        String touristId = registerTourist("turista.publico@example.com", "5550008888", "3008880002");
        assertThat(publicStatus(touristId)).isEqualTo(HttpStatus.NOT_FOUND);

        String providerId = registerProvider(
                "Prestadora Bloqueada", "prestadora.bloqueada@example.com", "903555777-8", "3008880003");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(changeStatus(adminToken, providerId, "BLOQUEADO")).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(publicStatus(providerId)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String createGuide(String token, String email, String dniNumber, String license) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", "GUIA");
        payload.put("fullName", "Guía Público");
        payload.put("email", email);
        payload.put("dniType", "CC");
        payload.put("dniNumber", dniNumber);
        payload.put("license", license);
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(payload)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private String registerTourist(String email, String dniNumber, String phone) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName",
                        "Ada Turista",
                        "email",
                        email,
                        "phone",
                        phone,
                        "password",
                        PASSWORD,
                        "dniType",
                        "CC",
                        "dniNumber",
                        dniNumber,
                        "preferredLanguage",
                        "es",
                        "nationality",
                        "Colombia"))
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
                        "businessType", "Agencia de viajes",
                        "websiteUrl", "https://prestadora.example.com"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private HttpStatusCode reviewProvider(String token, String userId, String decision) {
        return client.post()
                .uri("/api/v1/admin/users/" + userId + "/provider-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("decision", decision))
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode changeStatus(String token, String userId, String status) {
        return client.patch()
                .uri("/api/v1/admin/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("status", status))
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode publicStatus(String userId) {
        return client.get()
                .uri("/api/v1/public/users/" + userId)
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> publicProfile(String userId) {
        return client.get().uri("/api/v1/public/users/" + userId).retrieve().toEntity(Map.class);
    }

    private Map<?, ?> updateWebsite(String token, String websiteUrl) {
        return client.patch()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("websiteUrl", websiteUrl))
                .retrieve()
                .toEntity(Map.class)
                .getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> profile(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("profile");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> profile(Map<?, ?> response) {
        return (Map<String, Object>) response.get("profile");
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
}
