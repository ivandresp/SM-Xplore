package com.smxplore.sitesandservices.infrastructure.adapters.out.outbox;

import com.smxplore.sitesandservices.infrastructure.adapters.out.event.OutboxStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionJob {

    private final DatabaseClient databaseClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.retention-interval-ms:3600000}")
    public void purge() {
        purgeOnce()
                .doOnError(error -> log.error("Outbox retention job failed", error))
                .onErrorResume(error -> Mono.empty())
                .block(Duration.ofSeconds(60));
    }

    Mono<Void> purgeOnce() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.getRetentionDays()));
        Mono<Long> reservations = databaseClient.sql("""
                        DELETE FROM reservation_outbox
                        WHERE status IN ('PUBLISHED', 'FAILED')
                          AND created_at < :cutoff
                        """)
                .bind("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .fetch().rowsUpdated()
                .doOnNext(deleted -> log.info(
                        "Deleted {} reservation outbox rows older than {}",
                        deleted, cutoff));

        Query mongoQuery = Query.query(Criteria.where("status")
                .in(List.of(
                        OutboxStatus.PUBLISHED.name(),
                        OutboxStatus.FAILED.name()
                ))
                .and("createdAt").lt(cutoff));
        Mono<Long> catalog = mongoTemplate.remove(mongoQuery, OutboxEventDocument.class)
                .map(result -> result.getDeletedCount())
                .doOnNext(deleted -> log.info(
                        "Deleted {} catalog outbox rows older than {}",
                        deleted, cutoff));

        return Mono.when(reservations, catalog).then();
    }
}
