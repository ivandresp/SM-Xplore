package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CancelActivityUseCasePort {
    Mono<Activity> handle(UUID activityId);
}
