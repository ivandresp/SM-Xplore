package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangeActivityStatusUseCasePort {
    Mono<Activity> handle(UUID activityId, ActivityStatus newStatus);
}
