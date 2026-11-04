package com.smxplore.sitesandservices.domain.model.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Página del feed de reindexado. {@code updatedSince} es el corte usado en la consulta
 * ({@code null} significa exportación completa).
 */
public record CatalogExportPage(
        List<CatalogExportItem> content,
        int page,
        int size,
        long total,
        boolean hasNext,
        Instant updatedSince
) {

    public CatalogExportPage {
        if (content == null || content.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("export content is required and cannot contain null values");
        }
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
