package com.smxplore.sitesandservices.domain.model.catalog;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un registro del catálogo con el mismo payload que viaja en el evento de Kafka.
 *
 * <p>ms-05 reconstruye su proyección a partir de estos ítems cuando necesita un
 * reindex completo, y luego sigue con los eventos incrementales.
 */
public record CatalogExportItem(
        String eventType,
        UUID aggregateId,
        Instant updatedAt,
        Map<String, Object> payload
) {

    public CatalogExportItem {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("export eventType is required");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("export aggregateId is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("export updatedAt is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("export payload is required");
        }
        // LinkedHashMap + unmodifiableMap conservan el orden y aceptan valores nulos
        // (por ejemplo parentId ausente), cosa que Map.copyOf no permite.
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
