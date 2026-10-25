package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;

public class InvalidEventCapacityException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventCapacityException(String message) {
        super(message);
    }
}
