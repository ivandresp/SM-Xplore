package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import reactor.core.publisher.Mono;

public interface ListReservationsUseCase {

    Mono<PageResult<Reservation>> handle(ReservationFilter filter, Actor actor);
}
