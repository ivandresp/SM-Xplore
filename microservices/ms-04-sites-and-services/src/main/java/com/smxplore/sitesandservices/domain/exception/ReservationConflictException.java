package com.smxplore.sitesandservices.domain.exception;

/**
 * A reservation cannot be created or transitioned because capacity or state does not allow it.
 * Maps to HTTP 409.
 */
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(String message) {
        super(message);
    }
}
