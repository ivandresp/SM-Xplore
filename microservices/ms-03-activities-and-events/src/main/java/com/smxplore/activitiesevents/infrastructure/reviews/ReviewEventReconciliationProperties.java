package com.smxplore.activitiesevents.infrastructure.reviews;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning del job de conciliación de {@code reviews.review.changed}. Se valida al arrancar para que
 * una variable de entorno mal escrita falle el boot en lugar de dejar el job sin efecto.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.reviews.event-reconciliation")
public class ReviewEventReconciliationProperties {

    private boolean enabled = true;
    private long intervalMs = 60_000;
    private long initialDelayMs = 30_000;
    private int batchSize = 100;
    /**
     * Ventana de gracia: una reseña recién escrita puede estar todavía encolando su evento, así que
     * el job ignora las que se actualizaron hace menos de estos segundos.
     */
    private long graceSeconds = 60;

    @PostConstruct
    public void validate() {
        if (intervalMs < 1) {
            throw new IllegalStateException("app.reviews.event-reconciliation.interval-ms must be >= 1");
        }
        if (initialDelayMs < 0) {
            throw new IllegalStateException("app.reviews.event-reconciliation.initial-delay-ms must be >= 0");
        }
        if (batchSize < 1) {
            throw new IllegalStateException("app.reviews.event-reconciliation.batch-size must be >= 1");
        }
        if (graceSeconds < 0) {
            throw new IllegalStateException("app.reviews.event-reconciliation.grace-seconds must be >= 0");
        }
    }
}
