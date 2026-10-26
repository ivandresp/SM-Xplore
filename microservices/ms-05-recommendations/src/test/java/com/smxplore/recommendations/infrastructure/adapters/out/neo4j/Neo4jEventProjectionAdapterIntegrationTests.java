package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.AbstractNeo4jIntegrationTest;
import com.smxplore.recommendations.domain.model.catalog.CategoryScope;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.event.CategoryChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventMetadata;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.model.event.ReviewChangedEvent;
import com.smxplore.recommendations.domain.model.event.ReviewStatus;
import com.smxplore.recommendations.domain.model.event.ServiceChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.ports.out.EventProjectionPort;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Neo4jEventProjectionAdapterIntegrationTests extends AbstractNeo4jIntegrationTest {

    @Autowired
    private EventProjectionPort projectionPort;

    @Autowired
    private Driver driver;

    @Test
    void appliesAnEventOnceAndDeduplicatesByEventId() {
        UUID siteId = UUID.randomUUID();
        Instant updatedAt = now();
        SiteChangedEvent event = siteEvent(siteId, "Playa de prueba", updatedAt);

        assertThat(projectionPort.apply(event).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(projectionPort.apply(event).block()).isEqualTo(ProjectionResult.DUPLICATE);

        try (Session session = driver.session()) {
            assertThat(session.run("""
                            MATCH (s:Sitio {siteId: $siteId})
                            RETURN s.name AS name, s.itemType AS itemType, s.status AS status
                            """, Map.of("siteId", siteId.toString()))
                    .single()
                    .get("name")
                    .asString()).isEqualTo("Playa de prueba");

            assertThat(session.run("""
                            MATCH (p:ProcessedEvent {eventId: $eventId})
                            RETURN count(p) AS count
                            """, Map.of("eventId", event.metadata().eventId().toString()))
                    .single()
                    .get("count")
                    .asLong()).isEqualTo(1);
        }
    }

    @Test
    void ignoresAnOlderEventForTheSameAggregate() {
        UUID siteId = UUID.randomUUID();
        Instant latest = now();
        SiteChangedEvent latestEvent = siteEvent(siteId, "Nombre nuevo", latest);
        SiteChangedEvent staleEvent = new SiteChangedEvent(
                metadata(EventTopics.CATALOG_SITE_CHANGED, siteId, latest.minusSeconds(60)),
                siteId,
                "Nombre viejo",
                UUID.randomUUID(),
                10.0,
                -75.0,
                ContentStatus.PUBLISHED,
                latest.minusSeconds(60)
        );

        assertThat(projectionPort.apply(latestEvent).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(projectionPort.apply(staleEvent).block()).isEqualTo(ProjectionResult.STALE);

        try (Session session = driver.session()) {
            assertThat(session.run("""
                            MATCH (s:Sitio {siteId: $siteId})
                            RETURN s.name AS name
                            """, Map.of("siteId", siteId.toString()))
                    .single()
                    .get("name")
                    .asString()).isEqualTo("Nombre nuevo");

            assertThat(session.run("""
                            MATCH (p:ProcessedEvent {eventId: $eventId})
                            RETURN p.outcome AS outcome
                            """, Map.of("eventId", staleEvent.metadata().eventId().toString()))
                    .single()
                    .get("outcome")
                    .asString()).isEqualTo("STALE");
        }
    }

    @Test
    void reviewEventsUpdateTheMaterializedAggregates() {
        UUID itemId = UUID.randomUUID();
        UUID touristOne = UUID.randomUUID();
        UUID touristTwo = UUID.randomUUID();
        UUID reviewOne = UUID.randomUUID();
        UUID reviewTwo = UUID.randomUUID();
        Instant firstAt = now().minusSeconds(120);
        Instant secondAt = now().minusSeconds(60);
        Instant hiddenAt = now();

        ReviewChangedEvent first = reviewEvent(
                reviewOne, itemId, touristOne, 5, ReviewStatus.PUBLISHED, firstAt);
        ReviewChangedEvent second = reviewEvent(
                reviewTwo, itemId, touristTwo, 3, ReviewStatus.PUBLISHED, secondAt);

        assertThat(projectionPort.apply(first).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(projectionPort.apply(second).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(itemSnapshot(itemId))
                .containsEntry("ratingCount", 2L)
                .containsEntry("ratingAvg", 4.0);

        ReviewChangedEvent hidden = reviewEvent(
                reviewOne, itemId, touristOne, 5, ReviewStatus.HIDDEN, hiddenAt);
        assertThat(projectionPort.apply(hidden).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(itemSnapshot(itemId))
                .containsEntry("ratingCount", 1L)
                .containsEntry("ratingAvg", 3.0);

        ReviewChangedEvent deleted = reviewEvent(
                reviewTwo, itemId, touristTwo, 3, ReviewStatus.DELETED, hiddenAt.plusSeconds(60));
        assertThat(projectionPort.apply(deleted).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(itemSnapshot(itemId))
                .containsEntry("ratingCount", 0L)
                .containsEntry("ratingAvg", 0.0);

        try (Session session = driver.session()) {
            assertThat(session.run("""
                            MATCH (:CatalogItem {itemId: $itemId})
                            RETURN count(*) AS count
                            """, Map.of("itemId", itemId.toString()))
                    .single()
                    .get("count")
                    .asLong()).isEqualTo(1);

            assertThat(session.run("""
                            MATCH (i:CatalogItem {itemId: $itemId})
                            RETURN i.lastReviewAt AS lastReviewAt
                            """, Map.of("itemId", itemId.toString()))
                    .single()
                    .get("lastReviewAt")
                    .isNull()).isTrue();
        }
    }

    @Test
    void itemsAreLinkedWhenTheirCategoryAndSiteArriveLater() {
        UUID categoryId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant base = now();

        ServiceChangedEvent service = new ServiceChangedEvent(
                metadata(EventTopics.CATALOG_SERVICE_CHANGED, serviceId, base),
                serviceId,
                siteId,
                "Servicio de prueba",
                "RESTAURANT",
                categoryId,
                10.0,
                -75.0,
                ContentStatus.PUBLISHED,
                base
        );
        CategoryChangedEvent category = new CategoryChangedEvent(
                metadata(EventTopics.CATALOG_CATEGORY_CHANGED, categoryId, base.plusSeconds(1)),
                categoryId,
                "Categoría de prueba",
                CategoryScope.SERVICE,
                null,
                base.plusSeconds(1)
        );
        SiteChangedEvent site = new SiteChangedEvent(
                metadata(EventTopics.CATALOG_SITE_CHANGED, siteId, base.plusSeconds(2)),
                siteId,
                "Sitio de prueba",
                categoryId,
                10.0,
                -75.0,
                ContentStatus.PUBLISHED,
                base.plusSeconds(2)
        );

        assertThat(projectionPort.apply(service).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(relationshipCount(
                "MATCH (:Servicio {serviceId: $id})-[:UBICADO_EN]->(:Sitio) RETURN count(*) AS count",
                serviceId)).isZero();
        assertThat(relationshipCount(
                "MATCH (:Servicio {serviceId: $id})-[:ES_DE_TIPO]->(:Categoria) RETURN count(*) AS count",
                serviceId)).isZero();

        assertThat(projectionPort.apply(category).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(relationshipCount(
                "MATCH (:Servicio {serviceId: $id})-[:ES_DE_TIPO]->(:Categoria) RETURN count(*) AS count",
                serviceId)).isEqualTo(1);

        assertThat(projectionPort.apply(site).block()).isEqualTo(ProjectionResult.APPLIED);
        assertThat(relationshipCount(
                "MATCH (:Servicio {serviceId: $id})-[:UBICADO_EN]->(:Sitio) RETURN count(*) AS count",
                serviceId)).isEqualTo(1);
        assertThat(relationshipCount(
                "MATCH (:Sitio {siteId: $id})-[:ES_DE_TIPO]->(:Categoria) RETURN count(*) AS count",
                siteId)).isEqualTo(1);
    }

    @Test
    void aReviewCanArriveBeforeItsCatalogItem() {
        UUID itemId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();
        Instant reviewAt = now();
        Instant catalogAt = reviewAt.minusSeconds(3600);

        ReviewChangedEvent review = reviewEvent(
                UUID.randomUUID(), itemId, touristId, 4, ReviewStatus.PUBLISHED, reviewAt);
        assertThat(projectionPort.apply(review).block()).isEqualTo(ProjectionResult.APPLIED);

        EventItemChangedEvent item = new EventItemChangedEvent(
                metadata(EventTopics.ACTIVITIES_EVENT_CHANGED, itemId, catalogAt),
                itemId,
                "Evento reconciliado",
                UUID.randomUUID(),
                UUID.randomUUID(),
                10.0,
                -75.0,
                25000,
                catalogAt.plusSeconds(3600),
                null,
                ContentStatus.PUBLISHED,
                catalogAt
        );
        assertThat(projectionPort.apply(item).block()).isEqualTo(ProjectionResult.APPLIED);

        try (Session session = driver.session()) {
            Record record = session.run("""
                            MATCH (e:Evento {eventId: $itemId})
                            RETURN e.name AS name, e.status AS status, e.ratingCount AS ratingCount
                            """, Map.of("itemId", itemId.toString()))
                    .single();
            assertThat(record.get("name").asString()).isEqualTo("Evento reconciliado");
            assertThat(record.get("status").asString()).isEqualTo("PUBLISHED");
            assertThat(record.get("ratingCount").asLong()).isEqualTo(1);
        }
    }

    private SiteChangedEvent siteEvent(UUID siteId, String name, Instant updatedAt) {
        return new SiteChangedEvent(
                metadata(EventTopics.CATALOG_SITE_CHANGED, siteId, updatedAt),
                siteId,
                name,
                UUID.randomUUID(),
                10.0,
                -75.0,
                ContentStatus.PUBLISHED,
                updatedAt
        );
    }

    private ReviewChangedEvent reviewEvent(
            UUID reviewId,
            UUID itemId,
            UUID touristId,
            int rating,
            ReviewStatus status,
            Instant updatedAt
    ) {
        return new ReviewChangedEvent(
                metadata(EventTopics.REVIEWS_REVIEW_CHANGED, reviewId, updatedAt),
                reviewId,
                touristId,
                ItemType.EVENT,
                itemId,
                rating,
                status,
                updatedAt
        );
    }

    private EventMetadata metadata(String eventType, UUID aggregateId, Instant occurredAt) {
        return new EventMetadata(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                aggregateId,
                "test-correlation",
                "test-producer"
        );
    }

    private Map<String, Object> itemSnapshot(UUID itemId) {
        try (Session session = driver.session()) {
            Record record = session.run("""
                            MATCH (i:CatalogItem {itemId: $itemId})
                            RETURN i.ratingCount AS ratingCount, i.ratingAvg AS ratingAvg
                            """, Map.of("itemId", itemId.toString()))
                    .single();
            return Map.of(
                    "ratingCount", record.get("ratingCount").asLong(),
                    "ratingAvg", record.get("ratingAvg").asDouble()
            );
        }
    }

    private long relationshipCount(String query, UUID id) {
        try (Session session = driver.session()) {
            return session.run(query, Map.of("id", id.toString()))
                    .single()
                    .get("count")
                    .asLong();
        }
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
