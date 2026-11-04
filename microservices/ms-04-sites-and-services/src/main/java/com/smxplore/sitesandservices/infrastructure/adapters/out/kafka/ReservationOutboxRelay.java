package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.BiFunction;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationOutboxRelay {

    private final DatabaseClient databaseClient;
    private final KafkaOutboxPublisher publisher;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;
    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void relay() {
        relayOnce()
                .doOnError(exception -> log.error("Reservation outbox relay failed", exception))
                .onErrorResume(exception -> Mono.empty())
                .block(Duration.ofSeconds(30));
    }

    Mono<Void> relayOnce() {
        return claimBatch()
                .concatMap(this::process)
                .then();
    }

    private Flux<ReservationOutboxEvent> claimBatch() {
        Instant now = Instant.now(clock);
        String sql = """
                WITH claimed AS (
                    SELECT event_id
                    FROM reservation_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                       OR (status = 'PROCESSING' AND locked_until < :now)
                    ORDER BY occurred_at ASC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE reservation_outbox o
                SET status = 'PROCESSING',
                    locked_by = :workerId,
                    locked_until = :lockedUntil
                FROM claimed c
                WHERE o.event_id = c.event_id
                RETURNING o.event_id, o.event_type, o.aggregate_id, o.event_json,
                          o.occurred_at, o.attempts
                """;
        return databaseClient.sql(sql)
                .bind("now", offset(now))
                .bind("limit", properties.getBatchSize())
                .bind("workerId", workerId)
                .bind("lockedUntil", offset(now.plus(properties.getLeaseDuration())))
                .map(rowMapper())
                .all();
    }

    private Mono<Void> process(ReservationOutboxEvent event) {
        return publisher.publish(event.eventType(), event.aggregateId().toString(), event.eventJson())
                .then(markPublished(event))
                .onErrorResume(exception -> markFailed(event, exception));
    }

    private Mono<Void> markPublished(ReservationOutboxEvent event) {
        String sql = """
                UPDATE reservation_outbox
                SET status = 'PUBLISHED',
                    published_at = :publishedAt,
                    locked_by = NULL,
                    locked_until = NULL,
                    last_error = NULL
                WHERE event_id = :eventId
                  AND status = 'PROCESSING'
                  AND locked_by = :workerId
                """;
        return databaseClient.sql(sql)
                .bind("publishedAt", offset(Instant.now(clock)))
                .bind("eventId", event.eventId())
                .bind("workerId", workerId)
                .fetch().rowsUpdated()
                .doOnSuccess(updated -> {
                    if (updated == 1L) {
                        metrics.bookingPublished(event.eventType());
                    } else {
                        log.warn("Reservation outbox lease lost while marking published: {}",
                                event.eventId());
                    }
                })
                .then();
    }

    private Mono<Void> markFailed(ReservationOutboxEvent event, Throwable exception) {
        int attempts = event.attempts() + 1;
        Instant nextAttemptAt = Instant.now(clock).plus(backoff(attempts));
        String sql = """
                UPDATE reservation_outbox
                SET status = CASE
                        WHEN attempts + 1 >= :maxAttempts THEN 'FAILED'
                        ELSE 'PENDING'
                    END,
                    attempts = attempts + 1,
                    next_attempt_at = :nextAttemptAt,
                    locked_by = NULL,
                    locked_until = NULL,
                    last_error = :lastError
                WHERE event_id = :eventId
                  AND status = 'PROCESSING'
                  AND locked_by = :workerId
                """;
        return databaseClient.sql(sql)
                .bind("maxAttempts", properties.getMaxAttempts())
                .bind("nextAttemptAt", offset(nextAttemptAt))
                .bind("lastError", errorMessage(exception))
                .bind("eventId", event.eventId())
                .bind("workerId", workerId)
                .fetch().rowsUpdated()
                .doOnSuccess(updated -> {
                    if (updated == 1L) {
                        metrics.bookingFailed(event.eventType());
                    } else {
                        log.warn("Reservation outbox lease lost while marking failed: {}",
                                event.eventId());
                    }
                })
                .then();
    }

    private Duration backoff(int attempts) {
        double multiplier = Math.pow(2.0, Math.max(0, attempts - 1));
        long initialMillis = Math.max(1L, properties.getInitialBackoff().toMillis());
        long maxMillis = Math.max(initialMillis, properties.getMaxBackoff().toMillis());
        long millis = Math.min(maxMillis, (long) (initialMillis * multiplier));
        return Duration.ofMillis(Math.max(1L, millis));
    }

    private String errorMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getName()
                : exception.getClass().getName() + ": " + message;
    }

    private BiFunction<Row, RowMetadata, ReservationOutboxEvent> rowMapper() {
        return (row, metadata) -> new ReservationOutboxEvent(
                row.get("event_id", UUID.class),
                row.get("event_type", String.class),
                row.get("aggregate_id", UUID.class),
                row.get("event_json", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                row.get("attempts", Integer.class)
        );
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ReservationOutboxEvent(
            UUID eventId,
            String eventType,
            UUID aggregateId,
            String eventJson,
            Instant occurredAt,
            int attempts
    ) {
    }
}
