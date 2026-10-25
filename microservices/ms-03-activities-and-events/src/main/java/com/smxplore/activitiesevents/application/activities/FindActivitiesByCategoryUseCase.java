package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesByCategoryUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class FindActivitiesByCategoryUseCase implements FindActivitiesByCategoryUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Flux<Activity> handle(String category) {
        if (category == null || category.isBlank()) {
            return Flux.error(new InvalidActivityRequestException("Category must not be blank."));
        }
        return activityRepo.findAllByCategory(category)
                .filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED);
    }
}
