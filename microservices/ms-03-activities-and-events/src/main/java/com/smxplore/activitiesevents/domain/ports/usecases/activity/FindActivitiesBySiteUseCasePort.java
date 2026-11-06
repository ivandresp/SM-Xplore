package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FindActivitiesBySiteUseCasePort {
    Mono<PageResult<Activity>> handle(UUID siteId, int page, int size);
}
