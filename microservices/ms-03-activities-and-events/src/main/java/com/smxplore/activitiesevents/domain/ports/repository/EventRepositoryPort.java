package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface EventRepositoryPort {

    Mono<Event> save(Event event);

    Mono<Event> findById(UUID id);
    Mono<Event> findByIdForUpdate(UUID id);

    Mono<Boolean> existsById(UUID id);

    Flux<Event> findAll();

    /** One page of the published catalogue, limited by the database. */
    Mono<PageResult<Event>> findPublishedPage(int page, int size);

    Flux<Event> searchByName(String name);

    Mono<PageResult<Event>> searchPublishedByNamePage(String name, int page, int size);

    Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds);
}
