package com.smxplore.activitiesevents.domain.model.activity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del catálogo de actividades. Todos son opcionales; {@code status} nulo
 * significa cualquier estado y el controlador decide cuándo se permite.
 */
public record ActivityFilter(
        String search,
        ActivityStatus status,
        String category,
        UUID siteId,
        UUID guideId,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Instant startsAfter,
        Instant startsBefore,
        int page,
        int size) {

    public static ActivityFilter published(int page, int size) {
        return new ActivityFilter(null, ActivityStatus.PUBLISHED, null, null, null,
                null, null, null, null, page, size);
    }
}
