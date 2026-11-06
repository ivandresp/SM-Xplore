package com.smxplore.activitiesevents.domain.model.catalog;

import java.time.Instant;
import java.util.List;

/**
 * Una página del export feed.
 *
 * <p>{@code updatedSince} viaja en la respuesta para que quien reanuda el feed sepa contra qué
 * marca se calculó y pueda pedir el siguiente delta sin ambigüedad.
 */
public record CatalogExportPage(
        List<CatalogExportItem> content,
        int page,
        int size,
        long total,
        boolean hasNext,
        Instant updatedSince) {

    public CatalogExportPage {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        content = List.copyOf(content);
    }
}
