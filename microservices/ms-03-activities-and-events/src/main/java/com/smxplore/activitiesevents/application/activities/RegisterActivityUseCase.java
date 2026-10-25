package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.RegisterActivityUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RegisterActivityUseCase implements RegisterActivityUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<Activity> handle(Activity activity) {
        return Mono.justOrEmpty(activity)
                .switchIfEmpty(Mono.error(new InvalidActivityRequestException("Activity cannot be null")))
                .map(this::validate)
                .flatMap(activityRepo::save);
    }

    private Activity validate(Activity activity) {
        activity.validate();
        return activity;
    }
}
