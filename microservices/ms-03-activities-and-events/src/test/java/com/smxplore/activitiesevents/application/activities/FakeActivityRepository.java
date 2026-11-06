package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class FakeActivityRepository implements ActivityRepositoryPort {

    private final Map<UUID, Activity> activities = new HashMap<>();

    @Override
    public Mono<Activity> save(Activity activity) {
        activities.put(activity.getId(), activity);
        return Mono.just(activity);
    }

    @Override
    public Mono<Activity> findById(UUID id) {
        return Mono.justOrEmpty(activities.get(id));
    }

    @Override
    public Mono<Activity> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return Mono.just(activities.containsKey(id));
    }

    @Override
    public Flux<Activity> findAll() {
        return Flux.fromIterable(activities.values());
    }

    @Override
    public Flux<Activity> findByIds(Collection<UUID> ids) {
        return Flux.fromIterable(ids).flatMap(this::findById);
    }

    @Override
    public Flux<Activity> findAllByGuideId(UUID guideId) {
        return findAll().filter(activity -> guideId.equals(activity.getGuideId()));
    }

    @Override
    public Mono<PageResult<Activity>> findPublishedPage(int page, int size) {
        var matches = publishedList().collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    private Flux<Activity> publishedList() {
        return findAll().filter(activity -> activity.getStatus() == com.smxplore.activitiesevents.domain.model.activity.ActivityStatus.PUBLISHED);
    }

    private static PageResult<Activity> page(List<Activity> matches, int page, int size) {
        int from = Math.min(page * size, matches.size());
        int to = Math.min(from + size, matches.size());
        return new PageResult<>(List.copyOf(matches.subList(from, to)), page, size, matches.size());
    }

    @Override
    public Flux<Activity> findAllByCategory(String category) {
        return findAll().filter(activity -> category.equalsIgnoreCase(activity.getCategory()));
    }

    @Override
    public Mono<PageResult<Activity>> findPublishedByCategoryPage(String category, int page, int size) {
        var matches = publishedList()
                .filter(activity -> category.equalsIgnoreCase(activity.getCategory()))
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    @Override
    public Flux<Activity> findAllBySiteId(UUID siteId) {
        return findAll().filter(activity -> siteId.equals(activity.getSiteId()));
    }

    @Override
    public Mono<PageResult<Activity>> findPublishedBySiteIdPage(UUID siteId, int page, int size) {
        var matches = publishedList()
                .filter(activity -> siteId.equals(activity.getSiteId()))
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    @Override
    public Mono<PageResult<Activity>> findUpdatedSince(Instant updatedSince, int page, int size) {
        var matches = findAll()
                .filter(activity -> updatedSince == null
                        || (activity.getUpdatedAt() != null && !activity.getUpdatedAt().isBefore(updatedSince)))
                // El feed se ordena por updated_at: un updatedAt nulo (registro legado) va primero.
                .sort(Comparator.comparing(Activity::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collectList().block();
        return Mono.just(page(matches == null ? List.of() : matches, page, size));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return findAll()
                .filter(activity -> activity.getSiteId() != null && siteIds.contains(activity.getSiteId()))
                .map(Activity::getId);
    }
}
