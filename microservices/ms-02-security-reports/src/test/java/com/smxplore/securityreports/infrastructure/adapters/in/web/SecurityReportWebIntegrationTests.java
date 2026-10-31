package com.smxplore.securityreports.infrastructure.adapters.in.web;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smxplore.securityreports.TestcontainersConfiguration;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import({TestcontainersConfiguration.class, SecurityReportWebIntegrationTests.JwtTestConfiguration.class})
class SecurityReportWebIntegrationTests {

    private static final String ISSUER = "https://securityreports.local";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityReportMongoRepository securityReportMongoRepository;

    @AfterEach
    void cleanDatabase() {
        securityReportMongoRepository.deleteAll().block();
    }

    @Test
    void createsReportWithTitleAndDescription() {
        var userId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/security-reports")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, "TOURIST", "Nestor"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "title": "Robo en la playa",
                          "description": "Un grupo de personas robó a un turista.",
                          "photosUrls": ["https://example.com/photo.jpg"]
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Robo en la playa")
                .jsonPath("$.description").isEqualTo("Un grupo de personas robó a un turista.")
                .jsonPath("$.user.id").isEqualTo(userId.toString())
                .jsonPath("$.photosUrls[0]").isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void rejectsListingAnotherUsersReports() {
        var requester = UUID.randomUUID();
        var otherUser = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/security-reports/by-user/{userId}", otherUser)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester, "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowsListingOwnReportsWithCursorPage() {
        var userId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/security-reports/by-user/{userId}?size=10", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.nextCursor").isEmpty();
    }

    @Test
    void rejectsInvalidCursor() {
        webTestClient.get()
                .uri("/api/v1/security-reports?status=ONGOING&cursor=not-a-cursor")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "ADMIN", "Admin"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsTokenWithoutSubject() {
        webTestClient.get()
                .uri("/api/v1/security-reports/by-user/{userId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(null, "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void returnsJsonErrorWhenTokenIsMissing() {
        webTestClient.get()
                .uri("/api/v1/security-reports/by-user/{userId}", UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized");
    }

    @Test
    void protectsAdminListingFromTourists() {
        webTestClient.get()
                .uri("/api/v1/security-reports?status=ONGOING")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private String bearer(UUID userId, String role, String name) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("role", role)
                .claim("name", name);
        if (userId != null) {
            claims.subject(userId.toString());
        }
        var parameters = JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        );
        var token = jwtEncoder.encode(parameters).getTokenValue();
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {
        @Bean
        JwtEncoder jwtEncoder(@Value("${app.security.jwt.secret}") String secret) {
            return new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
