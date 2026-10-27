package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.port.out.TotpAuthenticator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
        properties = {"app.security.lockout.enabled=false", "app.security.rate-limit.requests-per-minute=120"})
@Testcontainers
@EnabledIfDockerAvailable
class MfaFlowTest {

    private static final String EMAIL = "mfa@example.com";
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

    @Autowired
    private TotpAuthenticator totp;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void enrolsAndRequiresASecondFactorToLogin() {
        register();

        Map<String, Object> session = login();
        assertThat(session.get("mfaRequired")).isEqualTo(false);
        String accessToken = (String) session.get("accessToken");
        assertThat(accessToken).isNotBlank();

        Map<String, Object> status = get("/api/v1/users/me/mfa", accessToken);
        assertThat(status.get("enabled")).isEqualTo(false);

        Map<String, Object> enrollment = post("/api/v1/users/me/mfa/enroll", Map.of(), accessToken);
        String secret = (String) enrollment.get("secret");
        assertThat(secret).isNotBlank();
        assertThat((String) enrollment.get("otpauthUri")).startsWith("otpauth://totp/");
        @SuppressWarnings("unchecked")
        List<String> recoveryCodes = (List<String>) enrollment.get("recoveryCodes");
        assertThat(recoveryCodes).hasSize(10);

        assertThat(postStatus(
                        "/api/v1/users/me/mfa/confirm",
                        Map.of("code", totp.currentCode(secret, Instant.now())),
                        accessToken))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/users/me/mfa", accessToken).get("enabled")).isEqualTo(true);

        Map<String, Object> challenge = login();
        assertThat(challenge.get("mfaRequired")).isEqualTo(true);
        assertThat(challenge.get("accessToken")).isNull();
        String challengeToken = (String) challenge.get("mfaChallengeToken");
        assertThat(challengeToken).isNotBlank();

        String oneTimeCode = totp.currentCode(secret, Instant.now().plusSeconds(30));
        Map<String, Object> verified =
                post("/api/v1/auth/mfa/verify", Map.of("challengeToken", challengeToken, "code", oneTimeCode), null);
        String verifiedToken = (String) verified.get("accessToken");
        assertThat(verifiedToken).isNotBlank();

        assertThat(get("/api/v1/users/me", verifiedToken).get("email")).isEqualTo(EMAIL);

        Map<String, Object> replayChallenge = login();
        assertThat(postStatus(
                        "/api/v1/auth/mfa/verify",
                        Map.of(
                                "challengeToken",
                                (String) replayChallenge.get("mfaChallengeToken"),
                                "code",
                                oneTimeCode),
                        null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> recoveryChallenge = login();
        Map<String, Object> recovered = post(
                "/api/v1/auth/mfa/verify",
                Map.of(
                        "challengeToken",
                        (String) recoveryChallenge.get("mfaChallengeToken"),
                        "code",
                        recoveryCodes.get(0)),
                null);
        assertThat((String) recovered.get("accessToken")).isNotBlank();

        Map<String, Object> reusedChallenge = login();
        assertThat(postStatus(
                        "/api/v1/auth/mfa/verify",
                        Map.of(
                                "challengeToken",
                                (String) reusedChallenge.get("mfaChallengeToken"),
                                "code",
                                recoveryCodes.get(0)),
                        null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(postStatus(
                        "/api/v1/users/me/mfa/disable",
                        Map.of("password", PASSWORD, "code", recoveryCodes.get(1)),
                        verifiedToken))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> afterDisable = login();
        assertThat(afterDisable.get("mfaRequired")).isEqualTo(false);
        assertThat((String) afterDisable.get("accessToken")).isNotBlank();
    }

    private void register() {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", "Usuaria MFA",
                        "email", EMAIL,
                        "phone", "3005556677",
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", "5556667778",
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Map<String, Object> login() {
        return post("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String uri, String token) {
        ResponseEntity<Map> response = client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String uri, Map<String, Object> body, String token) {
        RestClient.RequestBodySpec spec = client.post().uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        ResponseEntity<Map> response = spec.body(body).retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    private HttpStatusCode postStatus(String uri, Map<String, Object> body, String token) {
        RestClient.RequestBodySpec spec = client.post().uri(uri);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(body).exchange((request, response) -> response.getStatusCode());
    }
}
