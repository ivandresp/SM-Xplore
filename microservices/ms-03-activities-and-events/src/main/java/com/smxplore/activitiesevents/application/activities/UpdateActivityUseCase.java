package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.application.outbox.BestEffortPublisher;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.UpdateActivityUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@RequiredArgsConstructor
public class UpdateActivityUseCase implements UpdateActivityUseCasePort {

    private final ActivityRepositoryPort activityRepo;
    private final ActivityChangedEventFactory activityEvents;
    private final BestEffortPublisher publisher;

    @Override
    public Mono<Activity> handle(UUID activityId, String name, String category, String description, BigDecimal price) {
        return activityRepo.findById(activityId)
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId)))
                .map(activity -> update(activity, name, category, description, price))
                .flatMap(activityRepo::save)
                .flatMap(saved -> publisher.publish(saved, "activities.activity.changed", saved.getId(),
                        activityEvents.activityChanged(saved)));
    }

    private Activity update(Activity activity, String name, String category, String description, BigDecimal price) {
        activity.updateDetails(name, category, description, price);
        return activity;
    }
}