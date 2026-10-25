package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.identity.application.port.out.PasswordResetNotifier;
import com.smxplore.users.identity.domain.model.Usuario;
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
class AdminPasswordResetTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String NEW_PASSWORD = "ResetPassword123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private ResetCapture resetCapture;

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void adminResetsThePasswordAndTheUserCanLoginWithTheNewOne() {
        String userId = registerTourist("reset@example.com", "9990001111", "3009990001");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpStatusCode accepted = client.post()
                .uri("/api/v1/admin/users/" + userId + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange((request, response) -> response.getStatusCode());
        assertThat(accepted).isEqualTo(HttpStatus.ACCEPTED);

        String token = resetCapture.tokenFor(UUID.fromString(userId));
        assertThat(token).isNotBlank();

        HttpStatusCode reset = client.post()
                .uri("/api/v1/auth/reset-password")
                .body(Map.of("token", token, "newPassword", NEW_PASSWORD))
                .exchange((request, response) -> response.getStatusCode());
        assertThat(reset).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginStatus("reset@example.com", INITIAL_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("reset@example.com", NEW_PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recordsTheResetInTheAuditLog() {
        String userId = registerTourist("reset.audit@example.com", "9990002222", "3009990002");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        client.post()
                .uri("/api/v1/admin/users/" + userId + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange((request, response) -> response.getStatusCode());

        Map<?, ?> page = client.get()
                .uri("/api/v1/admin/audit-logs?targetUserId=" + userId + "&action=CONTRASENA_RESTABLECIDA")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toEntity(Map.class)
                .getBody();

        assertThat(entries(page)).singleElement().satisfies(entry -> {
            assertThat(entry.get("actorEmail")).isEqualTo(ADMIN_EMAIL);
            assertThat(entry.get("actorRole")).isEqualTo("ADMIN");
        });
    }

    @Test
    void returnsNotFoundForAnUnknownUser() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpStatusCode status = client.post()
                .uri("/api/v1/admin/users/00000000-0000-0000-0000-000000000000/password-reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfig {

        @Bean
        @Primary
        ResetCapture resetCapture() {
            return new ResetCapture();
        }
    }

    static class ResetCapture implements PasswordResetNotifier {

        private final Map<UUID, String> tokens = new ConcurrentHashMap<>();

        @Override
        public void notify(Usuario user, String rawToken) {
            tokens.put(user.id().value(), rawToken);
        }

        String tokenFor(UUID userId) {
            return tokens.get(userId);
        }
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
                        INITIAL_PASSWORD,
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
