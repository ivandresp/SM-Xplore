package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxPostgresAdapter implements OutboxPort {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> enqueue(OutboxEvent event) {
        return databaseClient.sql("""
                        INSERT INTO outbox_events
                            (id, event_type, event_version, aggregate_id, correlation_id, payload, occurred_at,
                             status, attempts, next_attempt_at)
                        VALUES
                            (:id, :eventType, :eventVersion, :aggregateId, :correlationId,
                             CAST(:payload AS jsonb), :occurredAt, 'PENDING', 0, :occurredAt)
                        """)
                .bind("id", event.id())
                .bind("eventType", event.eventType())
                .bind("eventVersion", event.eventVersion())
                .bind("aggregateId", event.aggregateId())
                .bind("correlationId", event.correlationId())
                .bind("payload", event.payload())
                .bind("occurredAt", offset(event.occurredAt()))
                .fetch().rowsUpdated()
                .then();
    }

    /**
     * Reclaims the leases of workers that died mid-delivery and claims the due events.
     *
     * <p>An expired lease counts as a consumed attempt: otherwise a row that keeps killing its
     * worker would be reclaimed forever and never reach {@code FAILED}. When the attempt limit is
     * reached during the reclaim the row goes straight to {@code FAILED} with a {@code last_error}
     * that says why, so nothing loops silently.
     */
    @Override
    public Flux<ClaimedEvent> claim(int limit, Duration lease, int maxAttempts, String workerId) {
        Instant now = Instant.now();
        return expireLeases(now, maxAttempts)
                .thenMany(databaseClient.sql("""
                                WITH due AS (
                                    SELECT id
                                    FROM outbox_events
                                    WHERE status = 'PENDING'
                                      AND attempts < :maxAttempts
                                      AND next_attempt_at <= :now
                                    ORDER BY occurred_at, id
                                    FOR UPDATE SKIP LOCKED
                                    LIMIT :limit
                                )
                                UPDATE outbox_events event
                                SET status = 'PROCESSING',
                                    locked_by = :workerId,
                                    locked_until = :lockedUntil
                                FROM due
                                WHERE event.id = due.id
                                RETURNING event.id, event.event_type, event.event_version, event.aggregate_id,
                                          event.correlation_id, event.payload, event.occurred_at, event.attempts
                                """)
                        .bind("maxAttempts", maxAttempts)
                        .bind("now", offset(now))
                        .bind("limit", limit)
                        .bind("workerId", workerId)
                        .bind("lockedUntil", offset(now.plus(lease)))
                        .map((row, metadata) -> new ClaimedEvent(
                                new OutboxEvent(
                                        row.get("id", UUID.class),
                                        row.get("event_type", String.class),
                                        row.get("event_version", Integer.class),
                                        row.get("aggregate_id", UUID.class),
                                        row.get("correlation_id", UUID.class),
                                        row.get("payload", String.class),
                                        row.get("occurred_at", OffsetDateTime.class).toInstant()),
                                row.get("attempts", Integer.class)))
                        .all());
    }

    private Mono<Void> expireLeases(Instant now, int maxAttempts) {
        return databaseClient.sql("""
                        WITH expired AS (
                            SELECT id
                            FROM outbox_events
                            WHERE status = 'PROCESSING'
                              AND locked_until < :now
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE outbox_events event
                        SET attempts = event.attempts + 1,
                            status = CASE
                                WHEN event.attempts + 1 >= :maxAttempts THEN 'FAILED'
                                ELSE 'PENDING'
                            END,
                            next_attempt_at = :now,
                            locked_by = NULL,
                            locked_until = NULL,
                            last_error = COALESCE(event.last_error, 'lease expired before delivery')
                        FROM expired
                        WHERE event.id = expired.id
                        """)
                .bind("now", offset(now))
                .bind("maxAttempts", maxAttempts)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Boolean> markPublished(UUID eventId, String workerId) {
        return databaseClient.sql("""
                        UPDATE outbox_events
                        SET status = 'PUBLISHED',
                            published_at = now(),
                            locked_by = NULL,
                            locked_until = NULL,
                            last_error = NULL
                        WHERE id = :id
                          AND status = 'PROCESSING'
                          AND locked_by = :workerId
                        """)
                .bind("id", eventId)
                .bind("workerId", workerId)
                .fetch().rowsUpdated()
                .map(updated -> updated == 1L);
    }

    @Override
    public Mono<Void> markFailed(UUID eventId, String workerId, int maxAttempts, Duration backoff, String error) {
        return databaseClient.sql("""
                        UPDATE outbox_events
                        SET status = CASE
                                WHEN attempts + 1 >= :maxAttempts THEN 'FAILED'
                                ELSE 'PENDING'
                            END,
                            attempts = attempts + 1,
                            next_attempt_at = :nextAttemptAt,
                            locked_by = NULL,
                            locked_until = NULL,
                            last_error = :error
                        WHERE id = :id
                          AND status = 'PROCESSING'
                          AND locked_by = :workerId
                        """)
                .bind("maxAttempts", maxAttempts)
                .bind("nextAttemptAt", offset(Instant.now().plus(backoff)))
                .bind("error", error == null ? null : error.length() > 1000 ? error.substring(0, 1000) : error)
                .bind("id", eventId)
                .bind("workerId", workerId)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> purgeTerminalBefore(Instant cutoff) {
        return databaseClient.sql("""
                        DELETE FROM outbox_events
                        WHERE status IN ('PUBLISHED', 'FAILED')
                          AND created_at < :cutoff
                        """)
                .bind("cutoff", offset(cutoff))
                .fetch().rowsUpdated()
                .map(updated -> updated == null ? 0L : updated);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
