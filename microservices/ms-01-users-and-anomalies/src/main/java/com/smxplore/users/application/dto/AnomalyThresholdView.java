package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.AnomalyThreshold;
import java.time.Instant;
import java.util.UUID;

public record AnomalyThresholdView(
        String type,
        String typeLabel,
        int thresholdValue,
        int windowSeconds,
        String severity,
        boolean enabled,
        Instant updatedAt,
        UUID updatedBy) {

    public static AnomalyThresholdView from(AnomalyThreshold threshold) {
        return new AnomalyThresholdView(
                threshold.type().code(),
                threshold.type().label(),
                threshold.thresholdValue(),
                threshold.windowSeconds(),
                threshold.severity().code(),
                threshold.enabled(),
                threshold.updatedAt(),
                threshold.updatedBy());
    }
}
