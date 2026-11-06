package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangeReservationStatusUseCase {

    Mono<Reservation> handle(
            UUID reservationId,
            ReservationStatus status,
            String reason,
            Actor actor,
            String correlationId
    );
}
