package com.smxplore.sitesandservices.infrastructure.adapters.out.outbox;

import com.smxplore.sitesandservices.infrastructure.adapters.out.event.OutboxStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class OutboxReprocessService {

    private final DatabaseClient databaseClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final Clock clock;

    public Mono<OutboxReprocessResult> reprocessFailed() {
        Instant now = Instant.now(clock);
        Mono<Long> reservations = databaseClient.sql("""
                        UPDATE reservation_outbox
                        SET status = 'PENDING',
                            attempts = 0,
                            next_attempt_at = :now,
                            locked_by = NULL,
                            locked_until = NULL,
                            last_error = NULL
                        WHERE status = 'FAILED'
                        """)
                .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .fetch().rowsUpdated();

        Query query = Query.query(Criteria.where("status").is(OutboxStatus.FAILED.name()));
        Update update = new Update()
                .set("status", OutboxStatus.PENDING.name())
                .set("attempts", 0)
                .set("nextAttemptAt", now)
                .unset("lastError")
                .unset("lockedBy")
                .unset("lockedUntil");
        Mono<Long> catalog = mongoTemplate.updateMulti(
                        query, update, OutboxEventDocument.class)
                .map(result -> result.getModifiedCount());

        return Mono.zip(reservations, catalog)
                .map(tuple -> new OutboxReprocessResult(
                        tuple.getT1(), tuple.getT2()));
    }
}
