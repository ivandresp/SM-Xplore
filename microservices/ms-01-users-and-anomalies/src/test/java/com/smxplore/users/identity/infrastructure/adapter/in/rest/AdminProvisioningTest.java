package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.identity.application.port.out.AccountInvitationNotifier;
import com.smxplore.users.identity.domain.model.Usuario;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class AdminProvisioningTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String NEW_PASSWORD = "GuiaPassword123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private InvitationCapture invitationCapture;

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void createsAGuiaAndActivatesTheAccountThroughTheInvitation() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        ResponseEntity<Map> created =
                createInternalUser(adminToken, guiaPayload("guia.nuevo@example.com", "8880001111", "LIC-001"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("role")).isEqualTo("GUIA");
        String userId = String.valueOf(created.getBody().get("id"));

        assertThat(loginStatus("guia.nuevo@example.com", "cualquier-clave-larga"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = invitationCapture.tokenFor(UUID.fromString(userId));
        assertThat(token).isNotBlank();

        HttpStatusCode accepted = resetPassword(token, NEW_PASSWORD);
        assertThat(accepted).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(loginStatus("guia.nuevo@example.com", NEW_PASSWORD)).isEqualTo(HttpStatus.OK);

        assertThat(resetPassword(token, "OtraClaveLarga123!")).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createsGestoraAndAdminAccounts() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        Map<String, Object> gestora = basePayload("GESTORA_DESTINOS", "gestora@example.com");
        gestora.put("nit", "901333444-5");
        ResponseEntity<Map> createdGestora = createInternalUser(adminToken, gestora);
        assertThat(createdGestora.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdGestora.getBody().get("role")).isEqualTo("GESTORA_DESTINOS");

        Map<String, Object> admin = basePayload("ADMIN", "nuevo.admin@example.com");
        admin.put("dniType", "CC");
        admin.put("dniNumber", "8880002222");
        ResponseEntity<Map> createdAdmin = createInternalUser(adminToken, admin);
        assertThat(createdAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdAdmin.getBody().get("role")).isEqualTo("ADMIN");
    }

    @Test
    void rejectsDuplicatesAndRolesThatCannotBeCreatedByAnAdmin() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(createInternalUser(adminToken, guiaPayload("duplicado@example.com", "8880003333", "LIC-003"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(createInternalUser(adminToken, guiaPayload("duplicado@example.com", "8880004444", "LIC-004"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(createInternalUser(adminToken, guiaPayload("otro@example.com", "8880005555", null))
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(createInternalUser(adminToken, guiaPayload("tercero@example.com", "8880003333", "LIC-005"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> provider = basePayload("PRESTADORA_SERVICIOS", "prestadora.admin@example.com");
        assertThat(createInternalUser(adminToken, provider).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsTheAccountCreationInTheAuditLog() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        ResponseEntity<Map> created =
                createInternalUser(adminToken, guiaPayload("auditada@example.com", "8880006666", "LIC-006"));
        String userId = String.valueOf(created.getBody().get("id"));

        Map<?, ?> page = get(adminToken, "/api/v1/admin/audit-logs?targetUserId=" + userId);

        assertThat(entries(page)).singleElement().satisfies(entry -> {
            assertThat(entry.get("action")).isEqualTo("CUENTA_INTERNA_CREADA");
            Map<String, Object> details = (Map<String, Object>) entry.get("details");
            assertThat(details).containsEntry("rol", "GUIA");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfig {

        @Bean
        @Primary
        InvitationCapture invitationCapture() {
            return new InvitationCapture();
        }
    }

    static class InvitationCapture implements AccountInvitationNotifier {

        private final Map<UUID, String> tokens = new ConcurrentHashMap<>();

        @Override
        public void notify(Usuario user, String rawToken) {
            tokens.put(user.id().value(), rawToken);
        }

        String tokenFor(UUID userId) {
            return tokens.get(userId);
        }
    }

    private static Map<String, Object> guiaPayload(String email, String dniNumber, String license) {
        Map<String, Object> payload = basePayload("GUIA", email);
        payload.put("dniType", "CC");
        payload.put("dniNumber", dniNumber);
        if (license != null) {
            payload.put("license", license);
        }
        return payload;
    }

    private static Map<String, Object> basePayload(String role, String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", role);
        payload.put("fullName", "Cuenta Interna");
        payload.put("email", email);
        return payload;
    }

    private ResponseEntity<Map> createInternalUser(String token, Map<String, Object> payload) {
        try {
            return client.post()
                    .uri("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .toEntity(Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(exception.getResponseBodyAs(Map.class));
        }
    }

    private HttpStatusCode resetPassword(String token, String newPassword) {
        return client.post()
                .uri("/api/v1/auth/reset-password")
                .body(Map.of("token", token, "newPassword", newPassword))
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
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class)
                .getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<?, ?> page) {
        return (List<Map<String, Object>>) page.get("content");
    }
}
