package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.catalog;

import java.time.Instant;
import java.util.List;

/** Página del export feed. Contrato idéntico al de ms-04 para que ms-05 los trate igual. */
public record CatalogExportPageResponse(
        List<CatalogExportItemResponse> content,
        int page,
        int size,
        long total,
        boolean hasNext,
        Instant updatedSince) {
}
