package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;

public class InvalidActivityStatusTransitionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidActivityStatusTransitionException(String message) {
        super(message);
    }
}
