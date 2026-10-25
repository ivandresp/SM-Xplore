package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ConsultActivitiesUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ConsultActivitiesUseCase implements ConsultActivitiesUseCasePort {

    private final ActivityRepositoryPort activityRepo;

    @Override
    public Flux<Activity> handle() {
        return activityRepo.findAll();
    }
}
