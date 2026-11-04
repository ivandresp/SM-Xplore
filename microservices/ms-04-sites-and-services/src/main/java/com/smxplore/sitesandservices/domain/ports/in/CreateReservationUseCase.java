package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import reactor.core.publisher.Mono;

public interface CreateReservationUseCase {

    Mono<Reservation> handle(
            Reservation reservation,
            String idempotencyKey,
            Actor actor,
            String correlationId
    );
}
