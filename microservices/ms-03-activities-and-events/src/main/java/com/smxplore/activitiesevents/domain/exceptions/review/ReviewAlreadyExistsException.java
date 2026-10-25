package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

public class ReviewAlreadyExistsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewAlreadyExistsException(String message) {
        super(message);
    }
}
