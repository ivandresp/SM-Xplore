package com.smxplore.activitiesevents.domain.exceptions.reservation;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation with id %s not found.".formatted(reservationId.toString()));
    }
}
