package com.smxplore.activitiesevents.domain.exceptions.event;

import java.io.Serial;

public class InvalidEventPriceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventPriceException(String message) {
        super(message);
    }
}
