package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Comparator;
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
    public Flux<Event> findByIds(Collection<UUID> ids) {
        return Flux.fromIterable(ids).flatMap(this::findById);
    }

    @Override
    public Mono<PageResult<Event>> findPage(com.smxplore.activitiesevents.domain.model.event.EventFilter filter) {
        var matches = findAll()
                .filter(event -> filter.status() == null || event.getStatus() == filter.status())
                .filter(event -> !hasText(filter.search())
                        || event.getName().toLowerCase().contains(filter.search().trim().toLowerCase()))
                .filter(event -> !hasText(filter.category())
                        || filter.category().equalsIgnoreCase(event.getCategory()))
                .filter(event -> filter.siteId() == null || filter.siteId().equals(event.getSiteId()))
                .filter(event -> filter.managerId() == null || filter.managerId().equals(event.getManagerId()))
                .filter(event -> filter.priceMin() == null
                        || event.getPrice().compareTo(filter.priceMin()) >= 0)
                .filter(event -> filter.priceMax() == null
                        || event.getPrice().compareTo(filter.priceMax()) <= 0)
                .filter(event -> filter.startsAfter() == null
                        || !event.getStartsAt().isBefore(filter.startsAfter()))
                .filter(event -> filter.startsBefore() == null
                        || !event.getStartsAt().isAfter(filter.startsBefore()))
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, filter.page(), filter.size()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
    public Mono<PageResult<Event>> findUpdatedSince(java.time.Instant updatedSince, int page, int size) {
        var matches = findAll()
                .filter(event -> updatedSince == null
                        || (event.getUpdatedAt() != null && !event.getUpdatedAt().isBefore(updatedSince)))
                // El feed se ordena por updated_at: un updatedAt nulo (registro legado) va primero.
                .sort(Comparator.comparing(Event::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return findAll()
                .filter(event -> event.getSiteId() != null && siteIds.contains(event.getSiteId()))
                .map(Event::getId);
    }

    @Override
    public Mono<Integer> applySiteSnapshot(UUID siteId, String siteName, UUID managerId, java.time.Instant siteUpdatedAt) {
        var matches = findAll()
                .filter(event -> siteId.equals(event.getSiteId()))
                .collectList().block();
        return Mono.just(matches == null ? 0 : matches.size());
    }
}
