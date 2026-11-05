package com.smxplore.recommendations.domain.model.reindex;

import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;

import java.util.List;

/**
 * One page of the catalog export published by ms-04, already decoded into the same events that the
 * Kafka consumers project. Reusing the projection keeps both paths writing identical nodes.
 */
public record CatalogExportPage(List<ProjectedEvent> content, int page, int size, long total, boolean hasNext) {

    public CatalogExportPage {
        if (page < 0) {
            throw new InvalidReindexException("catalog export page must be >= 0");
        }
        if (size < 1) {
            throw new InvalidReindexException("catalog export size must be >= 1");
        }
        if (total < 0) {
            throw new InvalidReindexException("catalog export total must be >= 0");
        }
        content = content == null ? List.of() : List.copyOf(content);
    }
}
