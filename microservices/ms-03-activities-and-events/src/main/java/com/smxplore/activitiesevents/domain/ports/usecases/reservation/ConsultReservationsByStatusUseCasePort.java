package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Flux;

public interface ConsultReservationsByStatusUseCasePort {
    Flux<Reservation> handle(ReservationStatus status, int limit);
}
