package com.smxplore.recommendations.domain.ports.out;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Deletes the idempotency markers that already processed events leave in the graph.
 * A marker only has to outlive the re-delivery window of its topic, not the whole history.
 */
public interface ProcessedEventRetentionPort {

    Mono<Long> deleteProcessedBefore(Instant cutoff, int batchSize);
}
