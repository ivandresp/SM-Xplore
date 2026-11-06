package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reads the catalog export published by ms-04. The bearer token of the caller is forwarded because
 * the export endpoint is reserved to administrators and ms-05 does not hold service credentials.
 */
public interface CatalogExportPort {

    Mono<CatalogExportPage> fetch(String bearerToken, CatalogEntity entity, int page, Instant updatedSince);
}
