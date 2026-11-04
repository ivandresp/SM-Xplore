package com.smxplore.securityreports.infrastructure.adapters.out.event;

import com.smxplore.securityreports.domain.model.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DomainEventJsonSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(DomainEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", event.eventVersion());
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("aggregateId", event.aggregateId().toString());
        envelope.put("correlationId", event.correlationId());
        envelope.put("producer", event.producer());
        envelope.put("payload", event.payload());
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize domain event " + event.eventId(), exception);
        }
    }
}
