package com.smxplore.recommendations.application.retention;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.ports.in.PurgeProcessedEventsUseCase;
import com.smxplore.recommendations.domain.ports.out.ProcessedEventRetentionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Deletes the idempotency markers in bounded batches so a large backlog never
 * becomes one long write transaction against Neo4j.
 */
@RequiredArgsConstructor
public class ProcessedEventRetentionService implements PurgeProcessedEventsUseCase {

    private final ProcessedEventRetentionPort retentionPort;

    @Override
    public Mono<Long> handle(Instant cutoff, int batchSize, int maxBatches) {
        validate(cutoff, batchSize, maxBatches);
        return purge(cutoff, batchSize, maxBatches, 0L);
    }

    private Mono<Long> purge(Instant cutoff, int batchSize, int remainingBatches, long deleted) {
        if (remainingBatches <= 0) {
            return Mono.just(deleted);
        }
        return retentionPort.deleteProcessedBefore(cutoff, batchSize)
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    long total = deleted + count;
                    return count < batchSize
                            ? Mono.just(total)
                            : purge(cutoff, batchSize, remainingBatches - 1, total);
                });
    }

    private void validate(Instant cutoff, int batchSize, int maxBatches) {
        if (cutoff == null) {
            throw new InvalidEventException("cutoff is required");
        }
        if (batchSize < 1) {
            throw new InvalidEventException("batchSize must be >= 1");
        }
        if (maxBatches < 1) {
            throw new InvalidEventException("maxBatches must be >= 1");
        }
    }
}
