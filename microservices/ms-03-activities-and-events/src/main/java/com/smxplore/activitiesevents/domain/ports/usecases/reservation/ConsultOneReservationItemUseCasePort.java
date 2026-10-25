package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultOneReservationItemUseCasePort {
    Mono<ReservationItem> handle(UUID reservationItemId);
}
