package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageSlotsUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ManageSlotsUseCase implements ManageSlotsUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<Activity> handle(UUID activityId, int maxCapacity) {
        return activityRepo.findById(activityId)
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId)))
                .map(activity -> changeMaxCapacity(activity, maxCapacity))
                .flatMap(activityRepo::save);
    }

    private Activity changeMaxCapacity(Activity activity, int maxCapacity) {
        activity.changeMaxCapacity(maxCapacity);
        return activity;
    }
}
