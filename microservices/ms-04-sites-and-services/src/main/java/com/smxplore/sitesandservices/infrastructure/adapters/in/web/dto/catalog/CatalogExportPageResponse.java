package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog;

import java.time.Instant;
import java.util.List;

public record CatalogExportPageResponse(
        List<CatalogExportItemResponse> content,
        int page,
        int size,
        long total,
        boolean hasNext,
        Instant updatedSince
) {
}
