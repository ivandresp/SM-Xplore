package com.smxplore.sitesandservices.infrastructure.adapters.out.outbox;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class OutboxMaintenanceIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private Clock clock;

    @Test
    void adminCanReprocessFailedEventsInBothOutboxes() {
        UUID reservationEventId = UUID.randomUUID();
        UUID catalogEventId = UUID.randomUUID();
        UUID reservationAggregateId = UUID.randomUUID();
        UUID catalogAggregateId = UUID.randomUUID();
        Instant now = Instant.now(clock);

        insertReservationEvent(reservationEventId, reservationAggregateId, "FAILED", now);
        mongoTemplate.save(outboxDocument(
                catalogEventId, catalogAggregateId, "FAILED", now)).block();

        clientFor(UUID.randomUUID(), "ADMIN")
                .post().uri("/api/v1/admin/outbox/reprocess")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.reservationEvents")
                .value(count -> assertThat(((Number) count).longValue()).isGreaterThanOrEqualTo(1))
                .jsonPath("$.catalogEvents")
                .value(count -> assertThat(((Number) count).longValue()).isGreaterThanOrEqualTo(1));

        clientFor(UUID.randomUUID(), "TURISTA")
                .post().uri("/api/v1/admin/outbox/reprocess")
                .exchange()
                .expectStatus().isForbidden();

        String reservationStatus = databaseClient.sql("""
                        SELECT status FROM reservation_outbox WHERE event_id = :eventId
                        """)
                .bind("eventId", reservationEventId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .block();
        assertThat(reservationStatus).isEqualTo("PENDING");

        OutboxEventDocument catalogEvent = mongoTemplate.findById(
                        catalogEventId.toString(), OutboxEventDocument.class)
                .block();
        assertThat(catalogEvent).isNotNull();
        assertThat(catalogEvent.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void retentionDeletesOldPublishedAndFailedEvents() {
        UUID oldReservationEventId = UUID.randomUUID();
        UUID oldCatalogEventId = UUID.randomUUID();
        Instant old = Instant.now(clock).minus(Duration.ofDays(40));

        insertReservationEvent(
                oldReservationEventId, UUID.randomUUID(), "PUBLISHED", old);
        mongoTemplate.save(outboxDocument(
                oldCatalogEventId, UUID.randomUUID(), "PUBLISHED", old)).block();

        OutboxRetentionJob job = new OutboxRetentionJob(
                databaseClient, mongoTemplate, properties, clock);
        job.purgeOnce().block();

        Long remainingReservationEvents = databaseClient.sql("""
                        SELECT COUNT(*) AS total FROM reservation_outbox
                        WHERE event_id = :eventId
                        """)
                .bind("eventId", oldReservationEventId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
        assertThat(remainingReservationEvents).isZero();

        OutboxEventDocument catalogEvent = mongoTemplate.findById(
                        oldCatalogEventId.toString(), OutboxEventDocument.class)
                .block();
        assertThat(catalogEvent).isNull();
    }

    private void insertReservationEvent(
            UUID eventId,
            UUID aggregateId,
            String status,
            Instant occurredAt
    ) {
        databaseClient.sql("""
                        INSERT INTO reservation_outbox (
                            event_id, event_type, aggregate_id, event_json,
                            occurred_at, status, attempts, next_attempt_at, created_at
                        )
                        VALUES (
                            :eventId, 'bookings.reservation.changed', :aggregateId, '{}',
                            :occurredAt, :status, 3, :occurredAt, :occurredAt
                        )
                        """)
                .bind("eventId", eventId)
                .bind("aggregateId", aggregateId)
                .bind("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .bind("status", status)
                .then()
                .block();
    }

    private OutboxEventDocument outboxDocument(
            UUID eventId,
            UUID aggregateId,
            String status,
            Instant createdAt
    ) {
        return OutboxEventDocument.builder()
                .id(eventId.toString())
                .eventType("catalog.category.changed")
                .aggregateId(aggregateId.toString())
                .eventJson("{}")
                .occurredAt(createdAt)
                .status(status)
                .attempts(3)
                .nextAttemptAt(createdAt)
                .createdAt(createdAt)
                .build();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
