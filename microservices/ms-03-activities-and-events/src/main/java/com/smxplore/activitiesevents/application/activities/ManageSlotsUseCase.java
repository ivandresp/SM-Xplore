package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityReservationConflictException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageSlotsUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ManageSlotsUseCase implements ManageSlotsUseCasePort {

    private final ActivityRepositoryPort activityRepo;
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Activity> handle(UUID activityId, int maxCapacity) {
        return activityRepo.findById(activityId)
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(activityId)))
                .flatMap(activity -> reservationRepo
                        .sumActivePeopleForItem(activityId, ReservationItemType.ACTIVITY)
                        .flatMap(reserved -> {
                            if (reserved > maxCapacity) {
                                return Mono.error(new ActivityReservationConflictException(
                                        "The new capacity (%d) is below the %d seats already reserved."
                                                .formatted(maxCapacity, reserved)));
                            }
                            activity.changeMaxCapacity(maxCapacity);
                            return activityRepo.save(activity);
                        }));
    }
}
