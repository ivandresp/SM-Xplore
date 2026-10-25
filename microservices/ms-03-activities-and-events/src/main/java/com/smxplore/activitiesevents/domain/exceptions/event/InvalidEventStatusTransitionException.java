package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;

public class InvalidEventStatusTransitionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventStatusTransitionException(String message) {
        super(message);
    }
}
