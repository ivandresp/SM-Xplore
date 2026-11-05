package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/** Raised when a non-administrator tries to change the visibility of a review. */
public class ReviewModerationNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewModerationNotAllowedException(String message) {
        super(message);
    }
}
