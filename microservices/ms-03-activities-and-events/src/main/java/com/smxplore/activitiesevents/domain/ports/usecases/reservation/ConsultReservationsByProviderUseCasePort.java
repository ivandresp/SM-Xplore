package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;

import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReservationsByProviderUseCasePort {

    /** Lists the reservations received by a provider; {@code status} is optional (null = any). */
    Flux<Reservation> handle(UUID providerId, ReservationStatus status, int limit);
}
