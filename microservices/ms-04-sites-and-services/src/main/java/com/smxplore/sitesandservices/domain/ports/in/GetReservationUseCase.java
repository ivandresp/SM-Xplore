package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetReservationUseCase {

    Mono<Reservation> handle(UUID reservationId, Actor actor);
}
