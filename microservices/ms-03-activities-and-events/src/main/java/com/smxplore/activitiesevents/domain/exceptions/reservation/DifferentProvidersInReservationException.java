package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class DifferentProvidersInReservationException extends RuntimeException {
    public DifferentProvidersInReservationException(String message) {
        super(message);
    }
}
