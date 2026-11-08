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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    void findsReportsNearACoordinateWithAnonymizedProjection() {
        var reporterId = UUID.randomUUID();
        createLocatedReport(reporterId, 10.3916, -75.4786);

        webTestClient.get()
                .uri("/api/v1/security-reports/nearby?latitude=10.392&longitude=-75.478&radiusKm=2")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].title").isEqualTo("Incidente cerca del parque")
                .jsonPath("$[0].status").isEqualTo("ONGOING")
                .jsonPath("$[0].latitude").isEqualTo(10.392)
                .jsonPath("$[0].longitude").isEqualTo(-75.479)
                .jsonPath("$[0].user").doesNotExist()
                .jsonPath("$[0].description").doesNotExist()
                .jsonPath("$[0].photosUrls").doesNotExist()
                .jsonPath("$[0].location").doesNotExist();
    }

    @Test
    void returnsTheSameAnonymizedProjectionToTheReportAuthor() {
        var reporterId = UUID.randomUUID();
        createLocatedReport(reporterId, 10.3916, -75.4786);

        webTestClient.get()
                .uri("/api/v1/security-reports/nearby?latitude=10.3916&longitude=-75.4786&radiusKm=2")
                .header(HttpHeaders.AUTHORIZATION, bearer(reporterId, "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].user").doesNotExist()
                .jsonPath("$[0].description").doesNotExist()
                .jsonPath("$[0].photosUrls").doesNotExist()
                .jsonPath("$[0].latitude").isEqualTo(10.392)
                .jsonPath("$[0].longitude").isEqualTo(-75.479);
    }

    @Test
    void rejectsNearbyQueriesAboveTheRadiusLimit() {
        webTestClient.get()
                .uri("/api/v1/security-reports/nearby?latitude=10.0&longitude=-75.0&radiusKm=20.1")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    void rejectsNearbyQueriesAboveTheSizeLimit() {
        webTestClient.get()
                .uri("/api/v1/security-reports/nearby?latitude=10.0&longitude=-75.0&size=51")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isBadRequest();
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

    @Test
    void countsEveryReportByStatusForStaff() {
        var reporter = UUID.randomUUID();
        var staff = UUID.randomUUID();

        createReport(reporter);
        var reported = createReport(reporter);
        var solved = createReport(reporter);

        closeReport(staff, reported);
        closeReport(staff, solved);
        solveReport(staff, solved);

        webTestClient.get()
                .uri("/api/v1/security-reports/counts")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff, "DESTINATION_MANAGER", "Gestora"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ONGOING").isEqualTo(1)
                .jsonPath("$.REPORTED").isEqualTo(1)
                .jsonPath("$.SOLVED").isEqualTo(1);
    }

    @Test
    void protectsTheCountsFromTouristsAndAnonymousUsers() {
        webTestClient.get()
                .uri("/api/v1/security-reports/counts")
                .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "TOURIST", "Nestor"))
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get()
                .uri("/api/v1/security-reports/counts")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private void createLocatedReport(UUID reporterId, double latitude, double longitude) {
        Map<String, Object> body = Map.of(
                "title", "Incidente cerca del parque",
                "description", "Reporte geolocalizado con datos personales del denunciante",
                "location", Map.of("latitude", latitude, "longitude", longitude),
                "photosUrls", List.of("https://example.com/photo.jpg")
        );

        webTestClient.post()
                .uri("/api/v1/security-reports")
                .header(HttpHeaders.AUTHORIZATION, bearer(reporterId, "TOURIST", "Nestor"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();
    }

    private String createReport(UUID reporterId) {
        Map<String, Object> created = webTestClient.post()
                .uri("/api/v1/security-reports")
                .header(HttpHeaders.AUTHORIZATION, bearer(reporterId, "TOURIST", "Nestor"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "title": "Robo en la playa",
                          "description": "Reporte de prueba"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        return String.valueOf(created.get("id"));
    }

    private void closeReport(UUID staffId, String reportId) {
        webTestClient.post()
                .uri("/api/v1/security-reports/{reportId}/close", reportId)
                .header(HttpHeaders.AUTHORIZATION, bearer(staffId, "DESTINATION_MANAGER", "Gestora"))
                .exchange()
                .expectStatus().isOk();
    }

    private void solveReport(UUID staffId, String reportId) {
        webTestClient.post()
                .uri("/api/v1/security-reports/{reportId}/solve", reportId)
                .header(HttpHeaders.AUTHORIZATION, bearer(staffId, "DESTINATION_MANAGER", "Gestora"))
                .exchange()
                .expectStatus().isOk();
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
