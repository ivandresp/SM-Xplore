package com.smxplore.activitiesevents.domain.exceptions.reservation;

import java.util.UUID;

public class ReservationItemNotFoundException extends RuntimeException {
    public ReservationItemNotFoundException(UUID reservationItemId) {
        super("Reservation item with id %s not found.".formatted(reservationItemId.toString()));
    }
}
