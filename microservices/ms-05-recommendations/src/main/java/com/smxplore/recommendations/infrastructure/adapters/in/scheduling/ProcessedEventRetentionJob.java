package com.smxplore.recommendations.infrastructure.adapters.in.scheduling;

import com.smxplore.recommendations.domain.ports.in.PurgeProcessedEventsUseCase;
import com.smxplore.recommendations.infrastructure.config.ProcessedEventRetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps {@code ProcessedEvent} bounded: the idempotency markers only need to live
 * longer than the broker's re-delivery window, so old ones are deleted hourly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.events.retention.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProcessedEventRetentionJob {

    private final PurgeProcessedEventsUseCase purgeProcessedEvents;
    private final ProcessedEventRetentionProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${app.events.retention.interval:1h}",
            initialDelayString = "${app.events.retention.initial-delay:1m}"
    )
    public void purge() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.getDays()));
        purgeProcessedEvents.handle(cutoff, properties.getBatchSize(), properties.getMaxBatches())
                .doOnNext(deleted -> log.info("Purged {} processed event markers older than {}", deleted, cutoff))
                .doOnError(error -> log.warn("Could not purge processed event markers", error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}
