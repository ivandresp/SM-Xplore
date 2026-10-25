package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<Boolean> existsById(UUID id) {
        return Mono.just(activities.containsKey(id));
    }

    @Override
    public Flux<Activity> findAll() {
        return Flux.fromIterable(activities.values());
    }

    @Override
    public Flux<Activity> findAllByCategory(String category) {
        return findAll().filter(activity -> category.equalsIgnoreCase(activity.getCategory()));
    }

    @Override
    public Flux<Activity> findAllBySiteId(UUID siteId) {
        return findAll().filter(activity -> siteId.equals(activity.getSiteId()));
    }
}
