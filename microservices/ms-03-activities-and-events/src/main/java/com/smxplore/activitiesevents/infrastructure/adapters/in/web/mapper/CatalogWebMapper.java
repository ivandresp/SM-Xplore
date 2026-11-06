package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.catalog.CatalogExportItem;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogExportPage;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.catalog.CatalogExportItemResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.catalog.CatalogExportPageResponse;
import org.springframework.stereotype.Component;

/** Traduce el modelo de dominio del feed a los DTOs públicos del endpoint. */
@Component
public class CatalogWebMapper {

    public CatalogExportItemResponse toResponse(CatalogExportItem item) {
        return new CatalogExportItemResponse(item.eventType(), item.eventVersion(), item.aggregateId(),
                item.updatedAt(), item.payload());
    }

    public CatalogExportPageResponse toResponse(CatalogExportPage page) {
        return new CatalogExportPageResponse(
                page.content().stream().map(this::toResponse).toList(),
                page.page(),
                page.size(),
                page.total(),
                page.hasNext(),
                page.updatedSince());
    }
}
