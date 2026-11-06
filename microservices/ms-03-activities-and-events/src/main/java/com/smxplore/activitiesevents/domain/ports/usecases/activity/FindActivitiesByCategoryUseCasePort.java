package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

public interface FindActivitiesByCategoryUseCasePort {
    Mono<PageResult<Activity>> handle(String category, int page, int size);
}
