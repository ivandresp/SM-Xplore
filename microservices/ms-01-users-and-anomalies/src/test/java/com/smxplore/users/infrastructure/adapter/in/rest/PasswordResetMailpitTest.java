package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Flujo completo de recuperación de contraseña contra un Mailpit real: el correo sale por SMTP, se captura del buzón
 * y el enlace se usa una sola vez para cambiar la contraseña.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.bootstrap-admin.enabled=false",
            "app.security.lockout.enabled=false",
            "app.security.rate-limit.requests-per-minute=120"
        })
@Testcontainers
@EnabledIfDockerAvailable
class PasswordResetMailpitTest {

    private static final String EMAIL = "reset.mailpit@example.com";
    private static final String OLD_PASSWORD = "ChangeMe123!";
    private static final String NEW_PASSWORD = "NuevaClave123!";
    private static final Pattern TOKEN = Pattern.compile("[?&]token=([^\\s&\\\"]+)");
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest")).withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/"
                        + POSTGRES.getDatabaseName());
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Value("${local.server.port}")
    private int port;

    private RestClient client;
    private RestClient mailpit;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        mailpit = RestClient.builder()
                .baseUrl("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025))
                .build();
    }

    @Test
    void sendsTheResetLinkThroughMailpitAndConsumesTheToken() {
        registerTourist();
        client.post()
                .uri("/api/v1/auth/forgot-password")
                .body(Map.of("email", EMAIL))
                .retrieve()
                .toBodilessEntity();

        String token = awaitResetToken();

        assertThat(postStatus("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD)))
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginStatus(OLD_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(NEW_PASSWORD)).isEqualTo(HttpStatus.OK);

        assertThat(postStatus("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", "OtraClave123!")))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String awaitResetToken() {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            String text = latestMessageText();
            if (text != null) {
                Matcher matcher = TOKEN.matcher(text);
                if (matcher.find()) {
                    return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
                }
            }
            sleep(500);
        }
        throw new AssertionError("Mailpit no recibió el correo de recuperación a tiempo");
    }

    @SuppressWarnings("unchecked")
    private String latestMessageText() {
        Map<String, Object> inbox = mailpit.get()
                .uri("/api/v1/messages")
                .retrieve()
                .toEntity(Map.class)
                .getBody();
        if (inbox == null || !(inbox.get("messages") instanceof List<?> messages) || messages.isEmpty()) {
            return null;
        }
        for (Object candidate : messages) {
            if (candidate instanceof Map<?, ?> message
                    && addresses(message.get("To")).contains(EMAIL)) {
                String id = String.valueOf(message.get("ID"));
                Map<String, Object> detail = mailpit.get()
                        .uri("/api/v1/message/{id}", id)
                        .retrieve()
                        .toEntity(Map.class)
                        .getBody();
                return detail == null ? null : String.valueOf(detail.get("Text"));
            }
        }
        return null;
    }

    private static List<String> addresses(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(address -> String.valueOf(address.get("Address")))
                .toList();
    }

    private void registerTourist() {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Usuario Mailpit",
                        "email", EMAIL,
                        "phone", "3007778899",
                        "password", OLD_PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "7778899001",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpStatusCode loginStatus(String password) {
        return client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", EMAIL, "password", password))
                .exchange((request, response) -> response.getStatusCode());
    }

    private HttpStatusCode postStatus(String uri, Map<String, Object> body) {
        return client.post().uri(uri).body(body).exchange((request, response) -> response.getStatusCode());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
