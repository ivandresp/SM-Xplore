package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of who moved a report to another status and when (MS02-15).
 * Persisted inline with the report so {@code close}/{@code solve} leave an audit trail
 * without a separate audit collection.
 */
public record StatusChange(UUID actorId, SecurityReportStatus from, SecurityReportStatus to,
                           Instant at, String correlationId) {

    public StatusChange {
        if (actorId == null) {
            throw new IllegalArgumentException("Status change actor is mandatory.");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("Status change requires origin and target statuses.");
        }
        if (from == to) {
            throw new IllegalArgumentException("Status change requires a different target status.");
        }
        if (at == null) {
            throw new IllegalArgumentException("Status change timestamp is mandatory.");
        }
    }
}
