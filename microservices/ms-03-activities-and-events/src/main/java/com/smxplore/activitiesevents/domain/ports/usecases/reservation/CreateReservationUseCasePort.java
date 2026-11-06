package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import reactor.core.publisher.Mono;

public interface CreateReservationUseCasePort {
    Mono<Reservation> handle(Reservation reservation);
}