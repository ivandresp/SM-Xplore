package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/**
 * The automatic review filter is unavailable and the configured policy is fail-closed. Maps to
 * HTTP 503.
 */
public class ReviewFilterUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewFilterUnavailableException() {
        super("The review moderation filter is temporarily unavailable. Please try again later.");
    }
}
