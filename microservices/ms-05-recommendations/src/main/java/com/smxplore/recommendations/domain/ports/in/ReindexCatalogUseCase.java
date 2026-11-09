package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.reindex.ReindexResult;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Rebuilds the catalog projection from the ms-04/ms-03 export without replaying Kafka.
 * Reserved to administrators; the actor is passed in so the run can be throttled per
 * administrator and audited (MS05-03).
 */
public interface ReindexCatalogUseCase {

    Mono<ReindexResult> handle(CatalogEntity entity, Instant updatedSince, String bearerToken, UUID actorId);
}
