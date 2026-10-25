package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReservationItemsUseCasePort {
    Flux<ReservationItem> handle(UUID reservationId);
}
