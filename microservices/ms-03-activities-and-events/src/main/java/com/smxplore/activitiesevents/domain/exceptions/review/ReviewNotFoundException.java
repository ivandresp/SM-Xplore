package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;
import java.util.UUID;

public class ReviewNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewNotFoundException(UUID reviewId) {
        super("Review %s was not found.".formatted(reviewId));
    }
}
