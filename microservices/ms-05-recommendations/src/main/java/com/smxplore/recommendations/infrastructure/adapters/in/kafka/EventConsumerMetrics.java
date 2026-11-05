package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EventConsumerMetrics {

    private final MeterRegistry meterRegistry;

    public EventConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordProcessed(String eventType, ProjectionResult result) {
        counter("recommendations.events.processed",
                "event_type", eventType,
                "result", result.name().toLowerCase(Locale.ROOT))
                .increment();
    }

    public void recordFailure(String topic) {
        counter("recommendations.events.failures", "topic", topic).increment();
    }

    public void recordDlq(String topic) {
        counter("recommendations.events.dlq", "topic", topic).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }
}
