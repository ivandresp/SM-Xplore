package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.events.consumer")
public class EventConsumerProperties {

    private boolean enabled = false;
    private int maxRetries = 3;
    private Duration initialBackoff = Duration.ofMillis(500);
    private Duration maxBackoff = Duration.ofSeconds(5);
    private Duration processingTimeout = Duration.ofSeconds(15);
    private double backoffMultiplier = 2.0;
    private int concurrency = 1;
    private String dlqSuffix = ".dlq";

    @PostConstruct
    public void validate() {
        validateMaxRetries();
        validateConcurrency();
        validateDurations();
        validateBackoffMultiplier();
        validateDlqSuffix();
    }

    private void validateMaxRetries() {
        if (maxRetries < 0) {
            throw new IllegalStateException("app.events.consumer.max-retries must be >= 0");
        }
    }

    private void validateConcurrency() {
        if (concurrency < 1) {
            throw new IllegalStateException("app.events.consumer.concurrency must be >= 1");
        }
    }

    private void validateDurations() {
        if (isNullOrNegative(initialBackoff)
                || isNullOrNegative(maxBackoff)
                || isNullOrNegativeOrZero(processingTimeout)) {
            throw new IllegalStateException("app.events.consumer durations must be positive");
        }
    }

    private void validateBackoffMultiplier() {
        if (!Double.isFinite(backoffMultiplier) || backoffMultiplier < 1.0) {
            throw new IllegalStateException("app.events.consumer.backoff-multiplier must be >= 1");
        }
    }

    private void validateDlqSuffix() {
        if (dlqSuffix == null || dlqSuffix.isBlank()) {
            throw new IllegalStateException("app.events.consumer.dlq-suffix is required");
        }
    }

    private boolean isNullOrNegative(Duration duration) {
        return duration == null || duration.isNegative();
    }

    private boolean isNullOrNegativeOrZero(Duration duration) {
        return duration == null || duration.isNegative() || duration.isZero();
    }
}
