package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.anomaly;

import java.time.OffsetDateTime;
import java.util.UUID;

record AnomalyRecord(
        UUID id,
        String type,
        String severity,
        String status,
        String description,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt,
        UUID resolvedBy,
        String resolutionNote) {}
