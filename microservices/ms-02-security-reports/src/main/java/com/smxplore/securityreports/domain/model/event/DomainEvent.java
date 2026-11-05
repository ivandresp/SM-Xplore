package com.smxplore.securityreports.domain.model.event;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;

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
        if (eventId == null) {
            throw new InvalidReportRequestException("eventId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidReportRequestException("eventType is required");
        }
        if (eventVersion != 1) {
            throw new InvalidReportRequestException("eventVersion must be 1");
        }
        if (occurredAt == null) {
            throw new InvalidReportRequestException("occurredAt is required");
        }
        if (aggregateId == null) {
            throw new InvalidReportRequestException("aggregateId is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new InvalidReportRequestException("correlationId is required");
        }
        if (producer == null || producer.isBlank()) {
            throw new InvalidReportRequestException("producer is required");
        }
        payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
    }
}
