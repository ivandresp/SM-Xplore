package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesBySiteUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class FindActivitiesBySiteUseCase implements FindActivitiesBySiteUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Flux<Activity> handle(UUID siteId) {
        return activityRepo.findAllBySiteId(siteId)
                .filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED);
    }
}
