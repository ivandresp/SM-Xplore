package com.smxplore.recommendations.infrastructure.adapters.in.web;

import com.smxplore.recommendations.AbstractNeo4jIntegrationTest;
import com.smxplore.recommendations.TestJwtConfiguration;
import com.smxplore.recommendations.TestSeedData;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.PreferenceResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationItemResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
@Import(TestJwtConfiguration.class)
class RecommendationWebIntegrationTests extends AbstractNeo4jIntegrationTest {

    private static final String ISSUER = "https://ms-05.local";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Driver driver;

    @Test
    void recommendationsForAnaExcludeReviewedAndExpiredItems() {
        RecommendationResponse response = clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?limit=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.touristId()).isEqualTo(TestSeedData.ANA);
        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.source()).isNotNull();
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items())
                .extracting(RecommendationItemResponse::itemId)
                .doesNotContain(TestSeedData.NOCHE_DE_JAZZ, TestSeedData.EVENTO_PASADO);
        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.score()).isBetween(0.0, 1.0);
                    assertThat(item.reason()).isNotBlank();
                });
    }

    @Test
    void recommendationsForNewTouristUseFallback() {
        RecommendationResponse response = clientFor(TestSeedData.NEW_TOURIST, "TOURIST")
                .get().uri("/api/v1/recommendations?limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo(RecommendationSource.FALLBACK);
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items())
                .extracting(RecommendationItemResponse::itemId)
                .doesNotContain(TestSeedData.EVENTO_PASADO);
    }

    @Test
    void nearbyRecommendationsExposeDistanceSignals() {
        RecommendationResponse response = clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?lat=10.3910&lng=-75.4790&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items())
                .allSatisfy(item -> assertThat(item.signals().distanceKm()).isNotNull());
    }

    @Test
    void filtersBySite() {
        RecommendationResponse response = clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?siteId=" + TestSeedData.PLAYA_ROSA + "&limit=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items())
                .allSatisfy(item -> assertThat(item.siteId()).isEqualTo(TestSeedData.PLAYA_ROSA));
    }

    @Test
    void adminCanQueryAnotherTourist() {
        RecommendationResponse response = clientFor(UUID.randomUUID(), "ADMIN")
                .get().uri("/api/v1/recommendations?touristId=" + TestSeedData.LUIS)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RecommendationResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.touristId()).isEqualTo(TestSeedData.LUIS);
    }

    @Test
    void touristCannotQueryAnotherTourist() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?touristId=" + TestSeedData.LUIS)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        webTestClient.get().uri("/api/v1/recommendations")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidLimitIsRejected() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?limit=100")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void locationMustBeProvidedAsPair() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?lat=10.3910")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void invalidLatitudeIsRejectedByDomainValidation() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?lat=91&lng=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void zeroLimitIsRejectedByDomainValidation() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .get().uri("/api/v1/recommendations?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void addsAndRemovesPreference() {
        PreferenceResponse created = clientFor(TestSeedData.ANA, "TOURIST")
                .post().uri("/api/v1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"categoryId":"%s"}
                        """.formatted(TestSeedData.CULTURA))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PreferenceResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.categoryId()).isEqualTo(TestSeedData.CULTURA);

        clientFor(TestSeedData.ANA, "TOURIST")
                .delete().uri("/api/v1/preferences/" + TestSeedData.CULTURA)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void languageComesFromAcceptLanguageAndNotFromTheJwt() {
        UUID tourist = UUID.randomUUID();

        clientFor(tourist, "TOURIST")
                .mutate()
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .build()
                .post().uri("/api/v1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"categoryId":"%s"}
                        """.formatted(TestSeedData.CULTURA))
                .exchange()
                .expectStatus().isCreated();

        try (Session session = driver.session()) {
            String language = session.run("""
                            MATCH (t:TuristaProfile {touristId: $touristId})
                            RETURN t.language AS language
                            """, Map.of("touristId", tourist.toString()))
                    .single()
                    .get("language")
                    .asString();
            assertThat(language).isEqualTo("en-us");
        }
    }

    @Test
    void unknownCategoryIsRejected() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .post().uri("/api/v1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"categoryId":"%s"}
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void missingCategoryIdIsRejectedByApplicationValidation() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .post().uri("/api/v1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registersView() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .post().uri("/api/v1/interactions/view/{itemType}/{itemId}",
                        ItemType.EVENT, TestSeedData.FESTIVAL_DEL_MAR)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void unknownItemViewIsRejected() {
        clientFor(TestSeedData.ANA, "TOURIST")
                .post().uri("/api/v1/interactions/view/{itemType}/{itemId}",
                        ItemType.EVENT, UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }

    private WebTestClient clientFor(UUID touristId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer(touristId, role))
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "es")
                .build();
    }

    private String bearer(UUID touristId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(touristId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", role)
                .claim("name", "Test User")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
