package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class ReservationItemsNotAllowedException extends RuntimeException {
    public ReservationItemsNotAllowedException(String message) {
        super(message);
    }
}
