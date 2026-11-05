package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;

public record ReindexResponse(
        CatalogEntity entity,
        int pages,
        int events,
        int applied,
        int duplicate,
        int stale
) {
}
