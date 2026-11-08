package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityFilter;
import com.smxplore.activitiesevents.domain.model.common.PageResult;

import reactor.core.publisher.Mono;

public interface ConsultActivitiesUseCasePort {
    Mono<PageResult<Activity>> handle(ActivityFilter filter);
}
