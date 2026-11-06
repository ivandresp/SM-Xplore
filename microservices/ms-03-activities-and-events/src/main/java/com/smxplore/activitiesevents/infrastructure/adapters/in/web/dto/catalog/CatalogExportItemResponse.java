package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.catalog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Entrada del export feed tal como la consume ms-04 (misma forma que su propio controller). */
public record CatalogExportItemResponse(
        String eventType,
        int eventVersion,
        UUID aggregateId,
        Instant updatedAt,
        Map<String, Object> payload) {
}
