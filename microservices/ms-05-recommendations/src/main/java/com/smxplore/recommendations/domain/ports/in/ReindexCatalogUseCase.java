package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.reindex.ReindexResult;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Rebuilds the catalog projection from ms-04 without replaying Kafka. Reserved to administrators.
 */
public interface ReindexCatalogUseCase {

    Mono<ReindexResult> handle(CatalogEntity entity, Instant updatedSince, String bearerToken);
}
