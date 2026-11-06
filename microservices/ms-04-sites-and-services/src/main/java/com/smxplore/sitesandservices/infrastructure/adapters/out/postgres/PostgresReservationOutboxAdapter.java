package com.smxplore.sitesandservices.infrastructure.adapters.out.postgres;

import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.ports.out.ReservationOutboxPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.event.DomainEventJsonSerializer;
import com.smxplore.sitesandservices.infrastructure.adapters.out.event.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Outbox transaccional PostgreSQL del servicio.
 *
 * <p>La tabla se llama {@code reservation_outbox} por herencia del primer flujo que la uso (las
 * reservas), pero es el outbox PG de todo ms-04: aqui se encolan tambien los eventos de zonas
 * ({@code catalog.zone.changed}). {@link com.smxplore.sitesandservices.infrastructure.adapters.out.kafka.ReservationOutboxRelay}
 * publica cada fila al topic igual a su {@code event_type}, sin asumir el agregado.
 */
@Component
@RequiredArgsConstructor
public class PostgresReservationOutboxAdapter implements ReservationOutboxPort {

    private final DatabaseClient databaseClient;
    private final DomainEventJsonSerializer serializer;
    private final Clock clock;

    @Override
    public Mono<Void> append(DomainEvent event) {
        Instant now = Instant.now(clock);
        String sql = """
                INSERT INTO reservation_outbox (
                    event_id, event_type, aggregate_id, event_json, occurred_at,
                    status, attempts, next_attempt_at, created_at
                )
                VALUES (
                    :eventId, :eventType, :aggregateId, :eventJson, :occurredAt,
                    :status, 0, :nextAttemptAt, :createdAt
                )
                """;
        return databaseClient.sql(sql)
                .bind("eventId", event.eventId())
                .bind("eventType", event.eventType())
                .bind("aggregateId", event.aggregateId())
                .bind("eventJson", serializer.serialize(event))
                .bind("occurredAt", offset(event.occurredAt()))
                .bind("status", OutboxStatus.PENDING.name())
                .bind("nextAttemptAt", offset(now))
                .bind("createdAt", offset(now))
                .fetch().rowsUpdated()
                .then();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
