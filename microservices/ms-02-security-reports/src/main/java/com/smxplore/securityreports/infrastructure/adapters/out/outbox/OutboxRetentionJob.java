package com.smxplore.securityreports.infrastructure.adapters.out.outbox;

import com.smxplore.securityreports.infrastructure.adapters.out.event.OutboxStatus;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.OutboxEventDocument;
import com.smxplore.securityreports.infrastructure.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionJob {

    private final ReactiveMongoTemplate mongoTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.retention-interval-ms:3600000}")
    public void purge() {
        purgeOnce()
                .doOnError(error -> log.error("Security report outbox retention job failed", error))
                .onErrorResume(error -> Mono.empty())
                .block(Duration.ofSeconds(60));
    }

    Mono<Void> purgeOnce() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.getRetentionDays()));
        Query query = Query.query(Criteria.where("status")
                .in(List.of(OutboxStatus.PUBLISHED.name(), OutboxStatus.FAILED.name()))
                .and("createdAt").lt(cutoff));
        return mongoTemplate.remove(query, OutboxEventDocument.class)
                .doOnNext(result -> log.info(
                        "Deleted {} security report outbox rows older than {}",
                        result.getDeletedCount(), cutoff))
                .then();
    }
}
