package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;

    public OutboxMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void published(String eventType) {
        counter("catalog.outbox.published", "event_type", eventType).increment();
    }

    public void failed(String eventType) {
        counter("catalog.outbox.failed", "event_type", eventType).increment();
    }

    public void bookingPublished(String eventType) {
        counter("booking.outbox.published", "event_type", eventType).increment();
    }

    public void bookingFailed(String eventType) {
        counter("booking.outbox.failed", "event_type", eventType).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }
}
