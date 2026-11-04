package com.smxplore.sitesandservices.domain.model.event;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DomainEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID aggregateId,
        String correlationId,
        String producer,
        Map<String, Object> payload
) {

    public DomainEvent {
        validateEventId(eventId);
        validateEventType(eventType);
        validateEventVersion(eventVersion);
        validateOccurredAt(occurredAt);
        validateAggregateId(aggregateId);
        validateCorrelationId(correlationId);
        validateProducer(producer);
        validatePayload(payload);
        payload = new LinkedHashMap<>(payload);
    }

    private static void validateEventId(UUID eventId) {
        if (eventId == null) {
            throw new InvalidCatalogException("eventId is required");
        }
    }

    private static void validateEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidCatalogException("eventType is required");
        }
    }

    private static void validateEventVersion(int eventVersion) {
        if (eventVersion != 1) {
            throw new InvalidCatalogException("eventVersion must be 1");
        }
    }

    private static void validateOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new InvalidCatalogException("event occurredAt is required");
        }
    }

    private static void validateAggregateId(UUID aggregateId) {
        if (aggregateId == null) {
            throw new InvalidCatalogException("event aggregateId is required");
        }
    }

    private static void validateCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new InvalidCatalogException("event correlationId is required");
        }
    }

    private static void validateProducer(String producer) {
        if (producer == null || producer.isBlank()) {
            throw new InvalidCatalogException("event producer is required");
        }
    }

    private static void validatePayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new InvalidCatalogException("event payload is required");
        }
    }
}
