package com.smxplore.activitiesevents.domain.exceptions.indicator;

import java.io.Serial;

public class InvalidDemandFilterException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidDemandFilterException(String message) {
        super(message);
    }
}
