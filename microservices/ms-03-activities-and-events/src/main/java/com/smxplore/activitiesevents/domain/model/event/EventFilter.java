package com.smxplore.activitiesevents.domain.model.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del catálogo de eventos. Todos son opcionales; {@code status} nulo
 * significa cualquier estado y el controlador decide cuándo se permite.
 */
public record EventFilter(
        String search,
        EventStatus status,
        String category,
        UUID siteId,
        UUID managerId,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Instant startsAfter,
        Instant startsBefore,
        int page,
        int size) {

    public static EventFilter published(int page, int size) {
        return new EventFilter(null, EventStatus.PUBLISHED, null, null, null,
                null, null, null, null, page, size);
    }
}
