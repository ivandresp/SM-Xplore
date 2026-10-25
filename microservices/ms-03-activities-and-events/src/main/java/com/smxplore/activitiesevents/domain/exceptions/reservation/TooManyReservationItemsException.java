package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class TooManyReservationItemsException extends RuntimeException {
    public TooManyReservationItemsException(String message) {
        super(message);
    }
}
