package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;

public class InvalidActivityPriceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidActivityPriceException(String message) {
        super(message);
    }
}
