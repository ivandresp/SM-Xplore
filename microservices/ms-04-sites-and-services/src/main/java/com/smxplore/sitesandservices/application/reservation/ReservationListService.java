package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.support.ReservationAccess;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListReservationsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReservationListService implements ListReservationsUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final ReservationAccess access;

    @Override
    public Mono<PageResult<Reservation>> handle(ReservationFilter filter, Actor actor) {
        return access.scope(filter, actor)
                .flatMap(reservationRepository::findAll);
    }
}
