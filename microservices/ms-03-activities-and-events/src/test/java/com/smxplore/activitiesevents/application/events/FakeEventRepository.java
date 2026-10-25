package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class FakeEventRepository implements EventRepositoryPort {

    private final Map<UUID, Event> events = new HashMap<>();

    @Override
    public Mono<Event> save(Event event) {
        events.put(event.getId(), event);
        return Mono.just(event);
    }

    @Override
    public Mono<Event> findById(UUID id) {
        return Mono.justOrEmpty(events.get(id));
    }

    @Override
    public Mono<Event> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return Mono.just(events.containsKey(id));
    }

    @Override
    public Flux<Event> findAll() {
        return Flux.fromIterable(events.values());
    }

    @Override
    public Flux<Event> searchByName(String name) {
        if (name == null) {
            return Flux.empty();
        }
        return findAll().filter(event -> event.getName() != null
                && event.getName().toLowerCase().contains(name.toLowerCase()));
    }
}
