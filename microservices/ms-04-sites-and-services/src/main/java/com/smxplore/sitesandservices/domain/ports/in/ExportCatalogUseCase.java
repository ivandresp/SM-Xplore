package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.catalog.CatalogEntity;
import com.smxplore.sitesandservices.domain.model.catalog.CatalogExportPage;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Feed paginado del catálogo para que ms-05 pueda reindexar su proyección.
 */
public interface ExportCatalogUseCase {

    Mono<CatalogExportPage> handle(
            CatalogEntity entity,
            Instant updatedSince,
            int page,
            int size,
            Actor actor
    );
}
