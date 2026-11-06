package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ManageAvailabilityUseCasePort {
    Mono<Activity> handle(UUID activityId, Instant startsAt, Instant endsAt);
}
