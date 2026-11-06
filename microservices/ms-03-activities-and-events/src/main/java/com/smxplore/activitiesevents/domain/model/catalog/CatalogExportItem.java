package com.smxplore.activitiesevents.domain.model.catalog;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Una entrada del export feed: la representación del agregado que ms-04 ingiere para su búsqueda.
 *
 * <p>El payload es exactamente el del evento vivo {@code activities.activity.changed} /
 * {@code activities.event.changed}, y el {@code updatedAt} superficie es la marca persistida del
 * agregado. Se conserva el orden de las claves para que la respuesta JSON sea estable entre
 * llamadas.
 */
public record CatalogExportItem(
        String eventType,
        int eventVersion,
        UUID aggregateId,
        Instant updatedAt,
        Map<String, Object> payload) {

    public CatalogExportItem {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(payload, "payload");
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
