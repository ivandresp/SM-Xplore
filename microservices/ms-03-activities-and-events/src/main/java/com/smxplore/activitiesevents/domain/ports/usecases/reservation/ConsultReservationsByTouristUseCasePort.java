package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReservationsByTouristUseCasePort {
    Flux<Reservation> handle(UUID touristId, int limit);
}
