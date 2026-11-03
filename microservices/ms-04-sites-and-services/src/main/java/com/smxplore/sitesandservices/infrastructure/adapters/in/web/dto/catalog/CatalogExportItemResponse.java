package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CatalogExportItemResponse(
        String eventType,
        UUID aggregateId,
        Instant updatedAt,
        Map<String, Object> payload
) {
}
