package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;

public class InvalidActivityRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidActivityRequestException(String message) {
        super(message);
    }
}
