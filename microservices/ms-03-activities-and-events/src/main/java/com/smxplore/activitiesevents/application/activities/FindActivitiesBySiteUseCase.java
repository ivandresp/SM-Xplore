package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesBySiteUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class FindActivitiesBySiteUseCase implements FindActivitiesBySiteUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<PageResult<Activity>> handle(UUID siteId, int page, int size) {
        return activityRepo.findPublishedBySiteIdPage(siteId, page, size);
    }
}
