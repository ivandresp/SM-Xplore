package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.application.outbox.OutboxPublisher;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ChangeActivityStatusUseCase implements ChangeActivityStatusUseCasePort {

    private final ActivityRepositoryPort activityRepo;
    private final ActivityChangedEventFactory activityEvents;
    private final OutboxPublisher publisher;

    @Override
    public Mono<Activity> handle(UUID activityId, ActivityStatus newStatus) {
        return activityRepo.findById(activityId)
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId)))
                .map(activity -> changeStatus(activity, newStatus))
                .flatMap(activityRepo::save)
                .flatMap(saved -> publisher.publish(saved, "activities.activity.changed", saved.getId(),
                        activityEvents.activityChanged(saved)));
    }

    private Activity changeStatus(Activity activity, ActivityStatus newStatus) {
        activity.changeStatus(newStatus);
        return activity;
    }
}