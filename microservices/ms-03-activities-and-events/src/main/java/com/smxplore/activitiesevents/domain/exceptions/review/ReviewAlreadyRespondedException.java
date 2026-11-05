package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

public class ReviewAlreadyRespondedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewAlreadyRespondedException(String message) {
        super(message);
    }
}
