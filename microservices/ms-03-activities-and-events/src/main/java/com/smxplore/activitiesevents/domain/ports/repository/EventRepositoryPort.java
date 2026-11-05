package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface EventRepositoryPort {

    Mono<Event> save(Event event);

    Mono<Event> findById(UUID id);
    Mono<Event> findByIdForUpdate(UUID id);

    Mono<Boolean> existsById(UUID id);

    Flux<Event> findAll();

    Flux<Event> findByIds(Collection<UUID> ids);

    /** One page of the published catalogue, limited by the database. */
    Mono<PageResult<Event>> findPublishedPage(int page, int size);

    Flux<Event> searchByName(String name);

    Mono<PageResult<Event>> searchPublishedByNamePage(String name, int page, int size);

    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);

    /**
     * Refreshes the site snapshot (name and manager) of every event of that site.
     *
     * <p>The update is ignored when {@code siteUpdatedAt} is not newer than the last snapshot
     * applied, so an out-of-order {@code catalog.site.changed} cannot revert a manager change.
     *
     * @return how many events were updated
     */
    Mono<Integer> applySiteSnapshot(UUID siteId, String siteName, UUID managerId, Instant siteUpdatedAt);
}
