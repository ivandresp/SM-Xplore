package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ActivityRepositoryPort {

    Mono<Activity> save(Activity activity);

    Mono<Activity> findById(UUID id);
    Mono<Activity> findByIdForUpdate(UUID id);

    Mono<Boolean> existsById(UUID id);

    Flux<Activity> findAll();

    Flux<Activity> findAllByCategory(String category);

    Flux<Activity> findAllBySiteId(UUID siteId);
}
