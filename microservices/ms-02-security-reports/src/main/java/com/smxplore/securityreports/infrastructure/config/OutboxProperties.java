package com.smxplore.securityreports.infrastructure.config;

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
    private String producer = "ms-02";
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
        if (producer == null || producer.isBlank()) {
            throw new IllegalStateException("app.outbox.producer is required");
        }
        if (pollIntervalMs < 1 || batchSize < 1 || maxAttempts < 1) {
            throw new IllegalStateException("app.outbox intervals, batch size and attempts must be >= 1");
        }
        if (initialBackoff == null || maxBackoff == null
                || initialBackoff.isNegative() || initialBackoff.isZero()
                || maxBackoff.isNegative() || maxBackoff.isZero()
                || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalStateException(
                    "app.outbox initial/max backoff must be positive and max >= initial");
        }
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalStateException("app.outbox.lease-duration must be positive");
        }
        if (retentionDays < 1 || retentionIntervalMs < 1) {
            throw new IllegalStateException("app.outbox retention values must be >= 1");
        }
    }
}
