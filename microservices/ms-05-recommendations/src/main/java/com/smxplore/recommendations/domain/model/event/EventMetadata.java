package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.exception.InvalidEventException;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope of every domain event consumed by ms-05.
 */
public record EventMetadata(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID aggregateId,
        String correlationId,
        String producer
) {

    public EventMetadata {
        validateEventId(eventId);
        validateEventType(eventType);
        validateEventVersion(eventVersion);
        validateOccurredAt(occurredAt);
        validateAggregateId(aggregateId);
        validateCorrelationId(correlationId);
        validateProducer(producer);
    }

    private static void validateEventId(UUID eventId) {
        if (eventId == null) {
            throw new InvalidEventException("eventId is required");
        }
    }

    private static void validateEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidEventException("eventType is required");
        }
    }

    private static void validateEventVersion(int eventVersion) {
        if (eventVersion != 1) {
            throw new InvalidEventException("Unsupported eventVersion: " + eventVersion);
        }
    }

    private static void validateOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new InvalidEventException("occurredAt is required");
        }
    }

    private static void validateAggregateId(UUID aggregateId) {
        if (aggregateId == null) {
            throw new InvalidEventException("aggregateId is required");
        }
    }

    private static void validateCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new InvalidEventException("correlationId is required");
        }
    }

    private static void validateProducer(String producer) {
        if (producer == null || producer.isBlank()) {
            throw new InvalidEventException("producer is required");
        }
    }
}
