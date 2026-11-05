package com.smxplore.activitiesevents.domain.model.outbox;

import java.time.Instant;
import java.util.UUID;

/** An event stored in the transactional outbox before it is relayed. */
public record OutboxEvent(
        UUID id,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        UUID correlationId,
        String payload,
        Instant occurredAt
) {
}
