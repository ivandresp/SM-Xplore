package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

public interface RegisterActivityUseCasePort {
    Mono<Activity> handle(Activity activity);
}
