package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxStatus;
import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final ReactiveMongoTemplate mongoTemplate;
    private final KafkaOutboxPublisher publisher;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void relay() {
        relayOnce()
                .doOnError(exception -> log.error("Outbox relay failed", exception))
                .onErrorResume(exception -> Mono.empty())
                .block(Duration.ofSeconds(30));
    }

    Mono<Void> relayOnce() {
        Query query = Query.query(Criteria.where("status").is(OutboxStatus.PENDING.name())
                        .and("nextAttemptAt").lte(Instant.now(clock)))
                .with(Sort.by(
                        Sort.Order.asc("occurredAt"),
                        Sort.Order.asc("createdAt")))
                .limit(properties.getBatchSize());

        return mongoTemplate.find(query, OutboxEventDocument.class)
                .concatMap(this::process)
                .then();
    }

    private Mono<Void> process(OutboxEventDocument event) {
        return publisher.publish(event)
                .then(markPublished(event))
                .onErrorResume(exception -> markFailed(event, exception));
    }

    private Mono<Void> markPublished(OutboxEventDocument event) {
        Query query = Query.query(Criteria.where("_id").is(event.getId()));
        Update update = new Update()
                .set("status", OutboxStatus.PUBLISHED.name())
                .set("publishedAt", Instant.now(clock))
                .unset("lastError");
        return mongoTemplate.updateFirst(query, update, OutboxEventDocument.class)
                .doOnSuccess(result -> metrics.published(event.getEventType()))
                .then();
    }

    private Mono<Void> markFailed(OutboxEventDocument event, Throwable exception) {
        int attempts = event.getAttempts() + 1;
        String status = attempts >= properties.getMaxAttempts()
                ? OutboxStatus.FAILED.name()
                : OutboxStatus.PENDING.name();
        Instant nextAttemptAt = Instant.now(clock).plus(backoff(attempts));

        Query query = Query.query(Criteria.where("_id").is(event.getId()));
        Update update = new Update()
                .set("status", status)
                .set("attempts", attempts)
                .set("nextAttemptAt", nextAttemptAt)
                .set("lastError", errorMessage(exception));
        return mongoTemplate.updateFirst(query, update, OutboxEventDocument.class)
                .doOnSuccess(result -> metrics.failed(event.getEventType()))
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
}
