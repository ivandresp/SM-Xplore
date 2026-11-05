package com.smxplore.activitiesevents.infrastructure.outbox;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning of the outbox relay. Validated at startup so a typo in an environment variable fails the
 * boot instead of silently disabling the retries.
 */
@Getter
@Setter
@Slf4j
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = false;
    private long pollIntervalMs = 2000;
    private int batchSize = 10;
    private int maxAttempts = 20;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(1);
    private Duration leaseDuration = Duration.ofMinutes(3);
    private Duration sendTimeout = Duration.ofSeconds(5);
    /** Tope de un ciclo completo; si el lote no cabe, se corta y el resto espera al siguiente. */
    private Duration cycleTimeout = Duration.ofSeconds(60);
    private int retentionDays = 30;
    private long retentionIntervalMs = 3_600_000;

    @PostConstruct
    public void validate() {
        if (pollIntervalMs < 1) {
            throw new IllegalStateException("app.outbox.poll-interval-ms must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalStateException("app.outbox.batch-size must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException("app.outbox.max-attempts must be >= 1");
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
        if (sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()) {
            throw new IllegalStateException("app.outbox.send-timeout must be positive");
        }
        if (cycleTimeout == null || cycleTimeout.isNegative() || cycleTimeout.isZero()) {
            throw new IllegalStateException("app.outbox.cycle-timeout must be positive");
        }
        if (batchSize * sendTimeout.toMillis() > cycleTimeout.toMillis()) {
            throw new IllegalStateException("app.outbox.cycle-timeout must be >= batch-size x send-timeout"
                    + " (batch-size=" + batchSize + ", send-timeout=" + sendTimeout
                    + ", cycle-timeout=" + cycleTimeout + ")");
        }
        if (leaseDuration.compareTo(sendTimeout.multipliedBy(batchSize)) <= 0) {
            // Un lote mas largo que el lease deja que otra instancia reclame filas que este worker
            // todavia esta publicando: se acepta pero se avisa para ajustar la configuracion.
            log.warn("app.outbox.lease-duration ({}) is not longer than batch-size x send-timeout ({});"
                            + " a slow batch can be reclaimed by another instance",
                    leaseDuration, sendTimeout.multipliedBy(batchSize));
        }
        if (retentionDays < 1) {
            throw new IllegalStateException("app.outbox.retention-days must be >= 1");
        }
        if (retentionIntervalMs < 1) {
            throw new IllegalStateException("app.outbox.retention-interval-ms must be >= 1");
        }
    }

    /** Exponential backoff of the given attempt (1-based), capped at {@link #maxBackoff}. */
    public Duration backoff(int attempt) {
        long initialMillis = Math.max(1L, initialBackoff.toMillis());
        long maxMillis = Math.max(initialMillis, maxBackoff.toMillis());
        double multiplier = Math.pow(2.0, Math.max(0, attempt - 1));
        long millis = Math.min(maxMillis, (long) (initialMillis * multiplier));
        return Duration.ofMillis(Math.max(1L, millis));
    }
}
