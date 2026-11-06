package com.smxplore.activitiesevents.domain.exceptions.reservation;

public class CancellationWindowClosedException extends RuntimeException {
    public CancellationWindowClosedException(String message) {
        super(message);
    }
}
