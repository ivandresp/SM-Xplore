package com.smxplore.activitiesevents.domain.ports.usecases.availability;

import com.smxplore.activitiesevents.domain.model.availability.Availability;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultAvailabilityUseCasePort {

    Mono<Availability> handle(UUID itemId, ReservationItemType itemType);
}
