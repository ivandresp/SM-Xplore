package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

public class ReviewAlreadyReportedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewAlreadyReportedException(String message) {
        super(message);
    }
}
