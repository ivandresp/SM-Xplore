package com.smxplore.recommendations.infrastructure.adapters.in.web;

import com.smxplore.recommendations.AbstractNeo4jIntegrationTest;
import com.smxplore.recommendations.TestJwtConfiguration;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.event.EventMetadata;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import com.smxplore.recommendations.domain.ports.out.CatalogExportPort;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.ReindexResponse;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
@Import({TestJwtConfiguration.class, ReindexWebIntegrationTests.StubCatalogExportConfiguration.class})
class ReindexWebIntegrationTests extends AbstractNeo4jIntegrationTest {

    private static final String ISSUER = "https://ms-05.local";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Driver driver;

    @Test
    void reindexesCatalogSitesForAdministrators() {
        UUID siteId = UUID.randomUUID();
        Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        StubCatalogExportPort.page = new CatalogExportPage(
                List.of(siteEvent(siteId, "Mirador de prueba", updatedAt)), 0, 200, 1, false);

        ReindexResponse response = webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer("ADMIN"))
                .build()
                .post().uri("/internal/admin/reindex?entity=SITES")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReindexResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.entity()).isEqualTo(CatalogEntity.SITES);
        assertThat(response.pages()).isEqualTo(1);
        assertThat(response.events()).isEqualTo(1);
        assertThat(response.applied()).isEqualTo(1);
        assertThat(response.duplicate()).isZero();

        try (Session session = driver.session()) {
            assertThat(session.run("""
                            MATCH (s:Sitio {siteId: $siteId})
                            RETURN s.name AS name
                            """, Map.of("siteId", siteId.toString()))
                    .single()
                    .get("name")
                    .asString()).isEqualTo("Mirador de prueba");
        }
    }

    @Test
    void forbidsTouristsFromReindexing() {
        webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer("TOURIST"))
                .build()
                .post().uri("/internal/admin/reindex?entity=SITES")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void rejectsAnUnknownEntity() {
        webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer("ADMIN"))
                .build()
                .post().uri("/internal/admin/reindex?entity=PROMOTIONS")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private String bearer(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(java.util.List.of("sm-xplore-services"))
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", role)
                .claim("name", "Admin de prueba")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static ProjectedEvent siteEvent(UUID siteId, String name, Instant updatedAt) {
        return new SiteChangedEvent(
                new EventMetadata(
                        UUID.randomUUID(),
                        EventTopics.CATALOG_SITE_CHANGED,
                        1,
                        updatedAt,
                        siteId,
                        "ms-05-reindex",
                        "ms-04"),
                siteId,
                name,
                UUID.randomUUID(),
                10.42,
                -75.53,
                ContentStatus.PUBLISHED,
                updatedAt);
    }

    @TestConfiguration
    static class StubCatalogExportConfiguration {

        @Bean
        @Primary
        StubCatalogExportPort stubCatalogExportPort() {
            return new StubCatalogExportPort();
        }
    }

    static class StubCatalogExportPort implements CatalogExportPort {

        static volatile CatalogExportPage page = new CatalogExportPage(List.of(), 0, 200, 0, false);
        static volatile String lastToken;

        @Override
        public Mono<CatalogExportPage> fetch(String bearerToken, CatalogEntity entity, int page,
                                             Instant updatedSince) {
            lastToken = bearerToken;
            return Mono.just(StubCatalogExportPort.page);
        }
    }
}
