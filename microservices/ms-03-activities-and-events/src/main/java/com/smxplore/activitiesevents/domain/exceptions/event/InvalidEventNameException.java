package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;

public class InvalidEventNameException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventNameException(String message) {
        super(message);
    }
}
