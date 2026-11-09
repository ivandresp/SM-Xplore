package com.smxplore.recommendations.infrastructure.adapters.out.web;

import com.smxplore.recommendations.domain.exception.DomainStateException;
import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.EventConsumerProperties;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogExportClientTest {

    private static final String TOKEN = "Bearer admin-token";
    private static final Instant UPDATED_AT = Instant.parse("2026-11-02T15:00:00Z");

    private DisposableServer server;
    private DisposableServer ms03Server;
    private final List<String> requestUris = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private final List<String> ms03RequestUris = new CopyOnWriteArrayList<>();
    private volatile String responseBody;
    private volatile int responseStatus;
    private volatile String ms03ResponseBody;
    private volatile int ms03ResponseStatus;
    private ReindexProperties properties;
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

        ms03ResponseStatus = 200;
        ms03ResponseBody = "{}";
        ms03Server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    ms03RequestUris.add(request.uri());
                    return response.status(ms03ResponseStatus)
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just(ms03ResponseBody));
                })
                .bindNow();

        JsonMapper mapper = JsonMapper.builder().build();
        properties = new ReindexProperties();
        properties.setCatalogBaseUrl("http://127.0.0.1:" + server.port());
        properties.setActivitiesBaseUrl("http://127.0.0.1:" + ms03Server.port());
        properties.setEventsBaseUrl("http://127.0.0.1:" + ms03Server.port());
        properties.setPageSize(50);
        client = new CatalogExportClient(
                WebClient.builder(),
                mapper,
                new ProjectionEventDecoder(
                        mapper,
                        new EventConsumerProperties(),
                        Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC)),
                properties);
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
        if (ms03Server != null) {
            ms03Server.disposeNow();
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

    @Test
    void routesActivitiesToMs03AndUsesItsV2Payload() {
        UUID activityId = UUID.randomUUID();
        ms03ResponseBody = activityPage(activityId, null);

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.ACTIVITIES, 0, UPDATED_AT).block();

        assertThat(page).isNotNull();
        assertThat(page.content().getFirst()).isInstanceOf(ActivityChangedEvent.class);
        ActivityChangedEvent activity = (ActivityChangedEvent) page.content().getFirst();
        assertThat(activity.activityId()).isEqualTo(activityId);
        assertThat(activity.categoryName()).isEqualTo("Deportes acuáticos");
        assertThat(activity.metadata().eventVersion()).isEqualTo(2);
        assertThat(activity.metadata().producer()).isEqualTo("ms-03-activities-and-events");
        assertThat(ms03RequestUris).hasSize(1);
        assertThat(ms03RequestUris.getFirst())
                .contains("entity=ACTIVITIES", "page=0", "size=50", "updatedSince=");
        assertThat(requestUris).isEmpty();
    }

    @Test
    void routesEventsToMs03() {
        UUID eventId = UUID.randomUUID();
        ms03ResponseBody = eventPage(eventId);

        CatalogExportPage page = client.fetch(TOKEN, CatalogEntity.EVENTS, 0, null).block();

        assertThat(page).isNotNull();
        assertThat(page.content().getFirst()).isInstanceOf(EventItemChangedEvent.class);
        EventItemChangedEvent eventItem = (EventItemChangedEvent) page.content().getFirst();
        assertThat(eventItem.itemId()).isEqualTo(eventId);
        assertThat(eventItem.categoryName()).isEqualTo("Cultura");
        assertThat(eventItem.metadata().eventType()).isEqualTo("activities.event.changed");
        assertThat(ms03RequestUris.getFirst()).contains("entity=EVENTS").doesNotContain("updatedSince");
        assertThat(requestUris).isEmpty();
    }

    @Test
    void mentionsTheProducerThatRejectedTheRequest() {
        ms03ResponseStatus = 403;
        ms03ResponseBody = "{}";

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.ACTIVITIES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("ms-03")
                        .hasMessageContaining("403"))
                .verify();
    }

    /** MS05-03: the ADMIN bearer never leaves for a host outside the allowlist. */
    @Test
    void refusesToSendTheTokenToAHostOutsideTheAllowlist() {
        properties.setAllowedHosts(List.of("internal.example"));

        StepVerifier.create(client.fetch(TOKEN, CatalogEntity.SITES, 0, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("host is not allowed"))
                .verify();

        assertThat(requestUris).isEmpty();
        assertThat(authorizationHeaders).isEmpty();
    }

    private static String activityPage(UUID activityId, String eventVersion) {
        String versionField = eventVersion == null ? "" : "\"eventVersion\": " + eventVersion + ",";
        return """
                {
                  "content": [
                    {
                      "eventType": "activities.activity.changed",
                      %s
                      "aggregateId": "%s",
                      "updatedAt": "%s",
                      "payload": {
                        "activityId": "%s",
                        "name": "Snorkel en Playa Rosa",
                        "category": "Deportes acuáticos",
                        "siteId": "%s",
                        "price": 80000,
                        "status": "PUBLISHED",
                        "updatedAt": "%s"
                      }
                    }
                  ],
                  "page": 0, "size": 50, "total": 1, "hasNext": false
                }
                """.formatted(versionField, activityId, UPDATED_AT, activityId, UUID.randomUUID(), UPDATED_AT);
    }

    private static String eventPage(UUID eventId) {
        return """
                {
                  "content": [
                    {
                      "eventType": "activities.event.changed",
                      "eventVersion": 2,
                      "aggregateId": "%s",
                      "updatedAt": "%s",
                      "payload": {
                        "eventId": "%s",
                        "name": "Festival del Mar",
                        "category": "Cultura",
                        "siteId": "%s",
                        "price": 100000,
                        "startsAt": "2026-11-20T18:00:00Z",
                        "endsAt": "2026-11-20T23:00:00Z",
                        "status": "PUBLISHED",
                        "updatedAt": "%s"
                      }
                    }
                  ],
                  "page": 0, "size": 50, "total": 1, "hasNext": false
                }
                """.formatted(eventId, UPDATED_AT, eventId, UUID.randomUUID(), UPDATED_AT);
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