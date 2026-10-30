package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Anomaly;
import java.time.Instant;
import java.util.UUID;

public record AnomalyView(
        UUID id,
        String type,
        String typeLabel,
        String severity,
        String status,
        String description,
        Instant detectedAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolutionNote) {

    public static AnomalyView from(Anomaly anomaly) {
        return new AnomalyView(
                anomaly.id(),
                anomaly.type().code(),
                anomaly.type().label(),
                anomaly.severity().code(),
                anomaly.status().code(),
                anomaly.description(),
                anomaly.detectedAt(),
                anomaly.resolvedAt(),
                anomaly.resolvedBy(),
                anomaly.resolutionNote());
    }
}
