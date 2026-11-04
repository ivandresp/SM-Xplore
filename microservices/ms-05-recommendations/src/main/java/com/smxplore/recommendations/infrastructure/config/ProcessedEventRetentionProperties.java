package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.events.retention")
public class ProcessedEventRetentionProperties {

    private boolean enabled = true;
    private int days = 30;
    private int batchSize = 1000;
    private int maxBatches = 20;
    private Duration interval = Duration.ofHours(1);
    private Duration initialDelay = Duration.ofMinutes(1);

    @PostConstruct
    public void validate() {
        if (days < 1) {
            throw new IllegalStateException("app.events.retention.days must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalStateException("app.events.retention.batch-size must be >= 1");
        }
        if (maxBatches < 1) {
            throw new IllegalStateException("app.events.retention.max-batches must be >= 1");
        }
        if (interval == null || interval.toMillis() < 1000) {
            throw new IllegalStateException("app.events.retention.interval must be at least 1s");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalStateException("app.events.retention.initial-delay cannot be negative");
        }
    }
}
