package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
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
    public Flux<Activity> findPublished() {
        return findAll().filter(activity -> activity.getStatus() == com.smxplore.activitiesevents.domain.model.activity.ActivityStatus.PUBLISHED);
    }

    @Override
    public Flux<Activity> findAllByCategory(String category) {
        return findAll().filter(activity -> category.equalsIgnoreCase(activity.getCategory()));
    }

    @Override
    public Flux<Activity> findPublishedByCategory(String category) {
        return findPublished().filter(activity -> category.equalsIgnoreCase(activity.getCategory()));
    }

    @Override
    public Flux<Activity> findAllBySiteId(UUID siteId) {
        return findAll().filter(activity -> siteId.equals(activity.getSiteId()));
    }

    @Override
    public Flux<Activity> findPublishedBySiteId(UUID siteId) {
        return findPublished().filter(activity -> siteId.equals(activity.getSiteId()));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return findAll()
                .filter(activity -> activity.getSiteId() != null && siteIds.contains(activity.getSiteId()))
                .map(Activity::getId);
    }
}
