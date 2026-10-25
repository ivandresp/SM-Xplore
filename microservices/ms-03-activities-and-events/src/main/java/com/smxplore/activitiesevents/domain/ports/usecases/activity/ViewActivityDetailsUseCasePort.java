package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ViewActivityDetailsUseCasePort {
    Mono<Activity> handle(UUID activityId);
}
