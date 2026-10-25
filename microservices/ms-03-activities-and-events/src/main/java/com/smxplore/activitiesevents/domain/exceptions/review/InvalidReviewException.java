package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

public class InvalidReviewException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidReviewException(String message) {
        super(message);
    }
}
