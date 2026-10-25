package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Flux;

import java.util.UUID;

public interface FindActivitiesBySiteUseCasePort {
    Flux<Activity> handle(UUID siteId);
}
