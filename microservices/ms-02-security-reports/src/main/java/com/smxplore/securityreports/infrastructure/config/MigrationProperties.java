package com.smxplore.securityreports.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bounds for the idempotent legacy backfill (MS02-16): it runs in batches and inside a
 * total time budget so a slow or huge collection cannot delay startup indefinitely.
 */
@ConfigurationProperties("app.migration")
public record MigrationProperties(boolean enabled, int batchSize, Duration timeout) {

    public MigrationProperties {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalStateException("app.migration.batch-size must be between 1 and 1000.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("app.migration.timeout must be positive.");
        }
    }
}
