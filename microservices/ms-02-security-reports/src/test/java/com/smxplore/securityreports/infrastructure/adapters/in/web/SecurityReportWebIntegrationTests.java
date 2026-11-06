package com.smxplore.securityreports.infrastructure.adapters.in.web;

import com.smxplore.securityreports.TestJwtConfiguration;
import com.smxplore.securityreports.TestcontainersConfiguration;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
class SecurityReportWebIntegrationTests {

    private static final String ISSUER = "sm-xplore-ms-01";
    private static final String AUDIENCE = "sm-xplore-services";

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
    void findsReportsNearACoordinate() {
        var userId = UUID.randomUUID();
        webTestClient.post()
                .uri("/api/v1/security-reports")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId, "TOURIST", "Nestor"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "title": "Incidente cerca del parque",
                          "description": "Reporte geolocalizado",
                          "location": {"latitude": 10.391, "longitude": -75.479}
                        }
                        """)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get()
                .uri("/api/v1/security-reports/nearby?latitude=10.392&longitude=-75.478&radiusKm=2")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].title").isEqualTo("Incidente cerca del parque");
    }

    @Test
    void returnsJsonErrorWhenTokenIsMissing() {
        webTestClient.get()
                .uri("/api/v1/security-reports/by-user/{userId}", UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized");
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
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("role", role)
                .claim("name", name);
        if (userId != null) {
            claims.subject(userId.toString());
        }
        var parameters = JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build()
        );
        var token = jwtEncoder.encode(parameters).getTokenValue();
        return "Bearer " + token;
    }
}
