package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;

public class InvalidActivityCapacityException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidActivityCapacityException(String message) {
        super(message);
    }
}
