package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.UUID;

/**
 * Adding one item is adding a single-item collection, so every validation lives
 * in {@link AddReservationItemsUseCase}.
 */
@RequiredArgsConstructor
public class AddReservationItemUseCase implements AddReservationItemUseCasePort {

    private final AddReservationItemsUseCasePort addReservationItems;

    @Override
    public Mono<ReservationItem> handle(UUID reservationId, ReservationItem item) {
        return addReservationItems.handle(reservationId, Collections.singletonList(item))
                .switchIfEmpty(Mono.error(new InvalidReservationItemException(
                        "The reservation item could not be saved.")))
                .single();
    }
}
