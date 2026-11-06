package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.common.PageResult;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ActivityRepositoryPort {

    Mono<Activity> save(Activity activity);

    Mono<Activity> findById(UUID id);
    Mono<Activity> findByIdForUpdate(UUID id);

    Mono<Boolean> existsById(UUID id);

    Flux<Activity> findAll();

    Flux<Activity> findByIds(Collection<UUID> ids);

    Flux<Activity> findAllByGuideId(UUID guideId);

    /** One page of the published catalogue, limited by the database. */
    Mono<PageResult<Activity>> findPublishedPage(int page, int size);

    Flux<Activity> findAllByCategory(String category);

    Mono<PageResult<Activity>> findPublishedByCategoryPage(String category, int page, int size);

    Flux<Activity> findAllBySiteId(UUID siteId);

    Mono<PageResult<Activity>> findPublishedBySiteIdPage(UUID siteId, int page, int size);

    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);

    /**
     * Página del feed de exportación ordenada por {@code updated_at} ascendente.
     *
     * <p>Con {@code updatedSince} nulo recorre el catálogo completo; con valor solo devuelve los
     * agregados modificados a partir de ese instante. La usan ms-04/ms-05 para sincronizar el
     * catálogo de forma incremental.
     */
    Mono<PageResult<Activity>> findUpdatedSince(Instant updatedSince, int page, int size);
}
