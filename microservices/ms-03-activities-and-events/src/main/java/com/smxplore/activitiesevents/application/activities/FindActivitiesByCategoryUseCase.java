package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesByCategoryUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class FindActivitiesByCategoryUseCase implements FindActivitiesByCategoryUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<PageResult<Activity>> handle(String category, int page, int size) {
        if (category == null || category.isBlank()) {
            return Mono.error(new InvalidActivityRequestException("Category must not be blank."));
        }
        return activityRepo.findPublishedByCategoryPage(category, page, size);
    }
}
