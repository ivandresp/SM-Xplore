package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

public class ReviewResponseNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewResponseNotAllowedException(String message) {
        super(message);
    }
}
