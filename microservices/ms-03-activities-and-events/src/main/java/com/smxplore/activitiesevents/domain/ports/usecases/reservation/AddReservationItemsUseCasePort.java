package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

public interface AddReservationItemsUseCasePort {
    Flux<ReservationItem> handle(UUID reservationId, Collection<ReservationItem> items);
}
