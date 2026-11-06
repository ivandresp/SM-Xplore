package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox of domain events in PostgreSQL.
 *
 * <p>The relay claims a batch with a lease: a row moves to {@code PROCESSING}, gets a
 * {@code locked_until} and only the worker that holds the lease can publish it or mark it
 * failed. A crash therefore leaves the row leased until the lease expires, and the next relay
 * cycle reclaims it instead of losing the event. Rows that exhaust their attempts become
 * {@code FAILED} and stay visible for an operator.
 */
public interface OutboxPort {

    /** Enqueues the event in the caller's transaction (see the transactional use case wrappers). */
    Mono<Void> enqueue(OutboxEvent event);

    /** Claims up to {@code limit} due events for this worker and holds them for {@code lease}. */
    Flux<ClaimedEvent> claim(int limit, Duration lease, int maxAttempts, String workerId);

    /** Marks a claimed event as published. Returns false when the lease was lost. */
    Mono<Boolean> markPublished(UUID eventId, String workerId);

    /**
     * Records a failed attempt, schedules the next one with an exponential backoff and marks the
     * event {@code FAILED} once {@code maxAttempts} is reached.
     */
    Mono<Void> markFailed(UUID eventId, String workerId, int maxAttempts, Duration backoff, String error);

    /** Deletes the events already delivered or permanently failed before {@code cutoff}. */
    Mono<Long> purgeTerminalBefore(Instant cutoff);

    static OutboxPort noop() {
        return new OutboxPort() {
            @Override
            public Mono<Void> enqueue(OutboxEvent event) {
                return Mono.empty();
            }

            @Override
            public Flux<ClaimedEvent> claim(int limit, Duration lease, int maxAttempts, String workerId) {
                return Flux.empty();
            }

            @Override
            public Mono<Boolean> markPublished(UUID eventId, String workerId) {
                return Mono.just(Boolean.FALSE);
            }

            @Override
            public Mono<Void> markFailed(UUID eventId, String workerId, int maxAttempts, Duration backoff,
                                         String error) {
                return Mono.empty();
            }

            @Override
            public Mono<Long> purgeTerminalBefore(Instant cutoff) {
                return Mono.just(0L);
            }
        };
    }

    /** A row taken by a worker; {@code attempts} is the count before this delivery. */
    record ClaimedEvent(OutboxEvent event, int attempts) {
    }
}
