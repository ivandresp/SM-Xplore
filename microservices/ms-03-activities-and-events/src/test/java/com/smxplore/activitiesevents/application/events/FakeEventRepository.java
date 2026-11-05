package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
    public Mono<PageResult<Event>> findPublishedPage(int page, int size) {
        var matches = publishedList().collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    private Flux<Event> publishedList() {
        return findAll().filter(event -> event.getStatus() == com.smxplore.activitiesevents.domain.model.event.EventStatus.PUBLISHED);
    }

    private static PageResult<Event> page(List<Event> matches, int page, int size) {
        int from = Math.min(page * size, matches.size());
        int to = Math.min(from + size, matches.size());
        return new PageResult<>(List.copyOf(matches.subList(from, to)), page, size, matches.size());
    }

    @Override
    public Flux<Event> searchByName(String name) {
        if (name == null) {
            return Flux.empty();
        }
        return findAll().filter(event -> event.getName() != null
                && event.getName().toLowerCase().contains(name.toLowerCase()));
    }

    @Override
    public Mono<PageResult<Event>> searchPublishedByNamePage(String name, int page, int size) {
        var matches = searchByName(name)
                .filter(event -> event.getStatus() == com.smxplore.activitiesevents.domain.model.event.EventStatus.PUBLISHED)
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return findAll()
                .filter(event -> event.getSiteId() != null && siteIds.contains(event.getSiteId()))
                .map(Event::getId);
    }
}
