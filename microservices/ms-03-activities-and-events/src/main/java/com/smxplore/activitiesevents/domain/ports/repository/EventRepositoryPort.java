package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EventRepositoryPort {

    Mono<Event> save(Event event);

    Mono<Event> findById(UUID id);
    Mono<Event> findByIdForUpdate(UUID id);

    Mono<Boolean> existsById(UUID id);

    Flux<Event> findAll();

    Flux<Event> searchByName(String name);
}
