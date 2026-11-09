package com.smxplore.sitesandservices.infrastructure.adapters.out.outbox;

import com.smxplore.sitesandservices.application.support.InMemoryRateLimiter;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.audit.SecurityAuditEntry;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.ports.out.SecurityAuditRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.event.OutboxStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * MS04-10: el reproceso de outbox es una operacion sensible. Acepta un limite (1..500), un modo
 * dry-run que solo cuenta, aplica un limite de peticiones por administrador y deja un registro
 * de auditoria con quien, cuando y cuantos eventos se reencolaron.
 */
@Component
@RequiredArgsConstructor
public class OutboxReprocessService {

    private static final int MAX_LIMIT = 500;

    private final DatabaseClient databaseClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final SecurityAuditRepositoryPort auditRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(5, Duration.ofMinutes(10));

    public Mono<OutboxReprocessResult> reprocessFailed(int limit, boolean dryRun, Actor actor) {
        if (actor == null || !actor.isAdmin()) {
            return Mono.error(new ForbiddenOperationException(
                    "Only an administrator can reprocess the outbox"));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            return Mono.error(new InvalidCatalogException(
                    "limit must be between 1 and " + MAX_LIMIT));
        }
        Instant now = Instant.now(clock);
        Mono<OutboxReprocessResult> operation = dryRun
                ? countFailed(limit)
                : resetFailed(now, limit);
        return rateLimiter.acquire("outbox-reprocess:" + actor.userId())
                .then(operation)
                .flatMap(result -> audit(actor, limit, dryRun, result).thenReturn(result));
    }

    private Mono<OutboxReprocessResult> resetFailed(Instant now, int limit) {
        Mono<Long> reservations = databaseClient.sql("""
                        WITH pending AS (
                            SELECT event_id
                            FROM reservation_outbox
                            WHERE status = 'FAILED'
                            ORDER BY occurred_at ASC, created_at ASC
                            LIMIT :limit
                        )
                        UPDATE reservation_outbox o
                        SET status = 'PENDING',
                            attempts = 0,
                            next_attempt_at = :now,
                            locked_by = NULL,
                            locked_until = NULL,
                            last_error = NULL
                        FROM pending
                        WHERE o.event_id = pending.event_id
                        """)
                .bind("limit", limit)
                .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .fetch().rowsUpdated();

        Mono<Long> catalog = failedCatalogIds(limit)
                .collectList()
                .flatMap(ids -> ids.isEmpty()
                        ? Mono.just(0L)
                        : mongoTemplate.updateMulti(
                                        Query.query(Criteria.where("_id").in(ids)),
                                        new Update()
                                                .set("status", OutboxStatus.PENDING.name())
                                                .set("attempts", 0)
                                                .set("nextAttemptAt", now)
                                                .unset("lastError")
                                                .unset("lockedBy")
                                                .unset("lockedUntil"),
                                        OutboxEventDocument.class)
                                .map(result -> result.getModifiedCount()));

        return Mono.zip(reservations, catalog)
                .map(tuple -> new OutboxReprocessResult(tuple.getT1(), tuple.getT2()))
                .doOnNext(result -> {
                    meterRegistry.counter("outbox.reprocess.events", "kind", "reservation")
                            .increment(result.reservationEvents());
                    meterRegistry.counter("outbox.reprocess.events", "kind", "catalog")
                            .increment(result.catalogEvents());
                });
    }

    private Mono<OutboxReprocessResult> countFailed(int limit) {
        Mono<Long> reservations = databaseClient.sql("""
                        SELECT COUNT(*) FROM (
                            SELECT event_id
                            FROM reservation_outbox
                            WHERE status = 'FAILED'
                            LIMIT :limit
                        ) AS failed
                        """)
                .bind("limit", limit)
                .map(row -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);

        Mono<Long> catalog = failedCatalogIds(limit)
                .count();

        return Mono.zip(reservations, catalog)
                .map(tuple -> new OutboxReprocessResult(tuple.getT1(), tuple.getT2()));
    }

    private Flux<String> failedCatalogIds(int limit) {
        Query query = Query.query(Criteria.where("status").is(OutboxStatus.FAILED.name()))
                .limit(limit);
        query.fields().include("_id");
        return mongoTemplate.find(query, OutboxEventDocument.class)
                .map(OutboxEventDocument::getId);
    }

    private Mono<Void> audit(Actor actor, int limit, boolean dryRun, OutboxReprocessResult result) {
        return auditRepository.append(new SecurityAuditEntry(
                UUID.randomUUID(),
                Instant.now(clock),
                dryRun ? "OUTBOX_REPROCESS_DRY_RUN" : "OUTBOX_REPROCESS",
                actor.userId(),
                actor.role().name(),
                "outbox",
                "limit=" + limit
                        + ", dryRun=" + dryRun
                        + ", reservationEvents=" + result.reservationEvents()
                        + ", catalogEvents=" + result.catalogEvents()));
    }
}
