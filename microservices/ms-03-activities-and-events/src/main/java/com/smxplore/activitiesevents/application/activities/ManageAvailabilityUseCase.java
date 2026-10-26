package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageAvailabilityUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ManageAvailabilityUseCase implements ManageAvailabilityUseCasePort {

    private final ActivityRepositoryPort activityRepo;
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Activity> handle(UUID activityId, Instant startsAt, Instant endsAt) {
        return activityRepo.findById(activityId)
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId)))
                .map(activity -> updateSchedule(activity, startsAt, endsAt))
                .flatMap(activityRepo::save)
                .flatMap(saved -> reservationRepo
                        .rescheduleItem(saved.getId(), ReservationItemType.ACTIVITY, saved.getStartsAt())
                        .thenReturn(saved));
    }

    private Activity updateSchedule(Activity activity, Instant startsAt, Instant endsAt) {
        activity.updateSchedule(startsAt, endsAt);
        return activity;
    }
}
