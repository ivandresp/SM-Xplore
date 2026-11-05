package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxPostgresAdapter implements OutboxPort {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> enqueue(OutboxEvent event) {
        return databaseClient.sql("""
                        INSERT INTO outbox_events
                            (id, event_type, event_version, aggregate_id, correlation_id, payload, occurred_at)
                        VALUES
                            (:id, :eventType, :eventVersion, :aggregateId, :correlationId,
                             CAST(:payload AS jsonb), :occurredAt)
                        """)
                .bind("id", event.id())
                .bind("eventType", event.eventType())
                .bind("eventVersion", event.eventVersion())
                .bind("aggregateId", event.aggregateId())
                .bind("correlationId", event.correlationId())
                .bind("payload", event.payload())
                .bind("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), java.time.ZoneOffset.UTC))
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Flux<OutboxEvent> findPending(int limit) {
        return databaseClient.sql("""
                        SELECT id, event_type, event_version, aggregate_id, correlation_id, payload, occurred_at
                        FROM outbox_events
                        WHERE published_at IS NULL
                        ORDER BY occurred_at, id
                        LIMIT :limit
                        """)
                .bind("limit", limit)
                .map((row, metadata) -> new OutboxEvent(
                        row.get("id", UUID.class),
                        row.get("event_type", String.class),
                        row.get("event_version", Integer.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("correlation_id", UUID.class),
                        row.get("payload", String.class),
                        row.get("occurred_at", OffsetDateTime.class).toInstant()))
                .all();
    }

    @Override
    public Mono<Void> markPublished(UUID eventId) {
        return databaseClient.sql("""
                        UPDATE outbox_events
                        SET published_at = now(), last_error = NULL
                        WHERE id = :id
                        """)
                .bind("id", eventId)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> markFailed(UUID eventId, String error) {
        return databaseClient.sql("""
                        UPDATE outbox_events
                        SET attempts = attempts + 1, last_error = :error
                        WHERE id = :id
                        """)
                .bind("id", eventId)
                .bind("error", error)
                .fetch().rowsUpdated()
                .then();
    }
}
