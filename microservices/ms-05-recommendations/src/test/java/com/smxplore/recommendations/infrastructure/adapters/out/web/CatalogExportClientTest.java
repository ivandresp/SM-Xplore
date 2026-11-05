package com.smxplore.recommendations.infrastructure.adapters.out.web;

import com.smxplore.recommendations.domain.exception.DomainStateException;
import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.ProjectionEventDecoder;
import com.smxplore.recommendations.infrastructure.config.ReindexProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogExportClientTest {

    private static final String TOKEN = "Bearer admin-token";
    private static final Instant UPDATED_AT = Instant.parse("2026-11-02T15:00:00Z");

    private DisposableServer server;
    private final List<String> requestUris = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private volatile String responseBody;
    private volatile int responseStatus;
    private CatalogExportClient client;

    @BeforeEach
    void setUp() {
        responseStatus = 200;
        responseBody = "{}";
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    requestUris.add(request.uri());
                    authorizationHeaders.add(request.requestHeaders().get("Authorization"));
                    return response.status(responseStatus)
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just(responseBody));
                })
                .bindNow();

        JsonMapper mapper = JsonMapper.builder().build();
        ReindexProperties properties = new ReindexProperties();
        properties.setCatalogBaseUrl("http://127.0.0.1:" + server.port());
        properties.setPageSize(50);
        client = new CatalogExportClient(
                WebClient.builder(),
                mapper,
                new ProjectionEventDecoder(mapper),
                properties);
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void decodesTheExportItemsIntoProjectionEvents() {
        UUID siteId = UUID.randomUUID();
        responseBody = """
                {
                  "content": [
                    {
                      "eventType": "catalog.site.changed",
                      "aggregateId": "%s",
                      "updatedAt": "%s",
                      "payload": {
                        "siteId": "%s",
                        "name": "Playa Cristal",
                        "categoryId": "%s",
                        "managerId": "%s",
                        "latitude": 11.31,
                        "longitude": -74.17,
                        "status": "PUBLISHED",
                        "updatedAt": "%s"
                      }
                    }
                  ],
                  "page": 0,
                  "size": 50,
                  "total": 1,
                  "hasNext": false,
                  "updatedSince": null
                }
                """.formatted(siteId, UPDATED_AT, siteId, UUID.randomUUID(), UUID.randomUUID(),
                UPDATED_AT);

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.SITES, 0, UPDATED_AT).block();

        assertThat(page).isNotNull();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).isInstanceOf(SiteChangedEvent.class);
        SiteChangedEvent event = (SiteChangedEvent) page.content().getFirst();
        assertThat(event.siteId()).isEqualTo(siteId);
        assertThat(event.name()).isEqualTo("Playa Cristal");
        assertThat(event.metadata().eventId())
                .isEqualTo(CatalogExportClient.reindexEventId("catalog.site.changed", siteId, UPDATED_AT));
        assertThat(event.metadata().correlationId()).isEqualTo(CatalogExportClient.CORRELATION_ID);
        assertThat(requestUris).hasSize(1);
        assertThat(requestUris.getFirst()).contains("entity=SITES", "page=0", "size=50", "updatedSince=");
        assertThat(authorizationHeaders).containsExactly(TOKEN);
    }

    @Test
    void omitsTheIncrementalFilterWhenNoInstantIsGiven() {
        responseBody = """
                {"content": [], "page": 0, "size": 50, "total": 0, "hasNext": false, "updatedSince": null}
                """;

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.CATEGORIES, 0, null).block();

        assertThat(page).isNotNull();
        assertThat(page.content()).isEmpty();
        assertThat(requestUris.getFirst()).doesNotContain("updatedSince");
    }

    @Test
    void rejectsExportItemsFromAnotherEntity() {
        responseBody = """
                {
                  "content": [
                    {
                      "eventType": "catalog.site.changed",
                      "aggregateId": "%s",
                      "updatedAt": "%s",
                      "payload": {"siteId": "%s"}
                    }
                  ],
                  "page": 0, "size": 50, "total": 1, "hasNext": false
                }
                """.formatted(UUID.randomUUID(), UPDATED_AT, UUID.randomUUID());

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.SERVICES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("returned eventType catalog.site.changed"))
                .verify();
    }

    @Test
    void translatesAForeignForbiddenResponse() {
        responseStatus = 403;
        responseBody = """
                {"detail": "Your role is not allowed to perform this operation."}
                """;

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.SITES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("403"))
                .verify();
    }

    @Test
    void translatesAForeignServerError() {
        responseStatus = 503;
        responseBody = "unavailable";

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.SITES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DomainStateException.class)
                        .hasMessageContaining("503"))
                .verify();
    }

    @Test
    void requiresATokenBeforeCallingMs04() {
        StepVerifier.create(client.fetch(" ", CatalogEntity.SITES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("authorization header is required"))
                .verify();
        assertThat(requestUris).isEmpty();
    }
    @Test
    void honoursTheEventVersionThatMs04Exports() {
        UUID siteId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        // El fixture siempre trae managerId (v2), asi que se declara una version distinta para
        // demostrar que el cliente usa el valor exportado y no la heuristica del payload.
        responseBody = sitePage(siteId, managerId, "2");

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.SITES, 0, UPDATED_AT).block();

        assertThat(page).isNotNull();
        SiteChangedEvent event = (SiteChangedEvent) page.content().getFirst();
        assertThat(event.metadata().eventVersion()).isEqualTo(2);
    }

    @Test
    void rejectsANonIntegerEventVersion() {
        UUID siteId = UUID.randomUUID();
        responseBody = sitePage(siteId, UUID.randomUUID(), "\"2\"");

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.SITES, 0, UPDATED_AT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("eventVersion"))
                .verify();
    }

    @Test
    void infersV2ForEverySiteOfAnOlderFeed() {
        UUID siteId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        responseBody = sitePage(siteId, managerId, null);

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.SITES, 0, UPDATED_AT).block();

        assertThat(page).isNotNull();
        SiteChangedEvent event = (SiteChangedEvent) page.content().getFirst();
        // Un feed antiguo sin version sigue siendo v2: un sitio sin gestora es valido.
        assertThat(event.metadata().eventVersion()).isEqualTo(2);
    }

    private static String sitePage(UUID siteId, UUID managerId, String eventVersion) {
        String versionField = eventVersion == null ? "" : "\"eventVersion\": " + eventVersion + ",";
        return """
                {
                  "content": [
                    {
                      "eventType": "catalog.site.changed",
                      %s
                      "aggregateId": "%s",
                      "updatedAt": "%s",
                      "payload": {
                        "siteId": "%s",
                        "name": "Playa Cristal",
                        "categoryId": "%s",
                        "managerId": "%s",
                        "latitude": 11.31,
                        "longitude": -74.17,
                        "status": "PUBLISHED",
                        "updatedAt": "%s"
                      }
                    }
                  ],
                  "page": 0,
                  "size": 50,
                  "total": 1,
                  "hasNext": false,
                  "updatedSince": null
                }
                """.formatted(versionField, siteId, UPDATED_AT, siteId, UUID.randomUUID(), managerId, UPDATED_AT);
    }
}