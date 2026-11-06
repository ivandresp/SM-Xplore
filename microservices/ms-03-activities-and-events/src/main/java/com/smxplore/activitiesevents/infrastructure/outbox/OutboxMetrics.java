package com.smxplore.activitiesevents.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Outbox counters exposed through {@code /actuator/prometheus}. */
@Component
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;

    public OutboxMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void published(String eventType) {
        counter("activities.outbox.published", eventType).increment();
    }

    public void failed(String eventType) {
        counter("activities.outbox.failed", eventType).increment();
    }

    public void exhausted(String eventType) {
        counter("activities.outbox.exhausted", eventType).increment();
    }

    public void leaseLost(String eventType) {
        counter("activities.outbox.lease_lost", eventType).increment();
    }

    private Counter counter(String name, String eventType) {
        return Counter.builder(name).tag("event_type", eventType).register(meterRegistry);
    }
}
