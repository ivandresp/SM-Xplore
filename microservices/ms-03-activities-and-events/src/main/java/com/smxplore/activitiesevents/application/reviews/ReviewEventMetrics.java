package com.smxplore.activitiesevents.application.reviews;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Contadores de publicación de {@code reviews.review.changed}, expuestos en {@code /actuator/prometheus}. */
@Component
public class ReviewEventMetrics {

    private final MeterRegistry meterRegistry;

    public ReviewEventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void published() {
        counter("activities.reviews.events.published").increment();
    }

    /** El enqueue falló: la reseña queda pendiente y la conciliación la reintentará. */
    public void failed() {
        counter("activities.reviews.events.failed").increment();
    }

    /** El job encontró reseñas sin evento confirmado y las re-despachó. */
    public void reconciled() {
        counter("activities.reviews.events.reconciled").increment();
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }
}
