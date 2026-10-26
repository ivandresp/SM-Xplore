package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CancelReservationUseCasePort {

    /**
     * @param staff when true the 24-hour window is skipped (admin, manager or the guide).
     */
    Mono<Reservation> handle(UUID reservationId, boolean staff);
}
