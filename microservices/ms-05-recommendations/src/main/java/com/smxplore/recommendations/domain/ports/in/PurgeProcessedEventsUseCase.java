package com.smxplore.recommendations.domain.ports.in;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Removes the {@code ProcessedEvent} markers created before the cutoff.
 * Returns how many markers were deleted so the scheduler can log the size of the purge.
 */
public interface PurgeProcessedEventsUseCase {

    Mono<Long> handle(Instant cutoff, int batchSize, int maxBatches);
}
