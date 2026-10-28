package com.smxplore.sitesandservices.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = true;
    private String producer = "ms-04";
    private long pollIntervalMs = 1000;
    private int batchSize = 50;
    private int maxAttempts = 20;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(1);
    private Duration leaseDuration = Duration.ofMinutes(3);
    private int retentionDays = 30;
    private long retentionIntervalMs = 3_600_000;

    @PostConstruct
    public void validate() {
        validateProducer();
        validatePollInterval();
        validateBatchSize();
        validateMaxAttempts();
        validateBackoffs();
        validateLeaseDuration();
        validateRetention();
    }

    private void validateProducer() {
        if (producer == null || producer.isBlank()) {
            throw new IllegalStateException("app.outbox.producer is required");
        }
    }

    private void validatePollInterval() {
        if (pollIntervalMs < 1) {
            throw new IllegalStateException("app.outbox.poll-interval-ms must be >= 1");
        }
    }

    private void validateBatchSize() {
        if (batchSize < 1) {
            throw new IllegalStateException("app.outbox.batch-size must be >= 1");
        }
    }

    private void validateMaxAttempts() {
        if (maxAttempts < 1) {
            throw new IllegalStateException("app.outbox.max-attempts must be >= 1");
        }
    }

    private void validateBackoffs() {
        if (initialBackoff == null || maxBackoff == null
                || initialBackoff.isNegative() || initialBackoff.isZero()
                || maxBackoff.isNegative() || maxBackoff.isZero()
                || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalStateException(
                    "app.outbox initial/max backoff must be positive and max >= initial");
        }
    }

    private void validateLeaseDuration() {
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalStateException("app.outbox.lease-duration must be positive");
        }
    }

    private void validateRetention() {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.outbox.retention-days must be >= 1");
        }
        if (retentionIntervalMs < 1) {
            throw new IllegalStateException("app.outbox.retention-interval-ms must be >= 1");
        }
    }
}
