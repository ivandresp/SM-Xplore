package com.smxplore.activitiesevents.application.availability;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.availability.Availability;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.availability.ConsultAvailabilityUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultAvailabilityUseCase implements ConsultAvailabilityUseCasePort {

    private final EventRepositoryPort eventRepo;
    private final ActivityRepositoryPort activityRepo;
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Availability> handle(UUID itemId, ReservationItemType itemType) {
        return switch (itemType) {
            case EVENT -> eventRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new EventNotFoundException(itemId)))
                    .flatMap(event -> availability(itemId, itemType, event.getMaxCapacity()));
            case ACTIVITY -> activityRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new ActivityNotFoundException(itemId)))
                    .flatMap(activity -> availability(itemId, itemType, activity.getMaxCapacity()));
        };
    }

    private Mono<Availability> availability(UUID itemId, ReservationItemType itemType, int maxCapacity) {
        return reservationRepo.sumActivePeopleForItem(itemId, itemType)
                .map(reserved -> {
                    var available = Math.max(0, maxCapacity - reserved);
                    return new Availability(itemId, itemType, maxCapacity, reserved, available);
                });
    }
}
