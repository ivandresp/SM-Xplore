package com.smxplore.activitiesevents.infrastructure.outbox;

import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
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
 * Drops delivered and permanently failed events after the retention window. Without it the table
 * grows forever; {@code FAILED} rows older than the window are already old news for the operators.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxRetentionJob {

    private final OutboxPort outbox;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.retention-interval-ms:3600000}")
    public void purge() {
        purgeOnce()
                .doOnError(error -> log.error("Outbox retention job failed", error))
                .onErrorResume(error -> Mono.empty())
                .block(Duration.ofSeconds(60));
    }

    Mono<Long> purgeOnce() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.getRetentionDays()));
        return outbox.purgeTerminalBefore(cutoff)
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        log.info("Deleted {} outbox rows older than {}", deleted, cutoff);
                    }
                });
    }
}
