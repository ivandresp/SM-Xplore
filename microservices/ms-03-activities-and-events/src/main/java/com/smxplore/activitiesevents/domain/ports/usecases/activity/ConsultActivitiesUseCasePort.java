package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Flux;

public interface ConsultActivitiesUseCasePort {
    Flux<Activity> handle();
}
