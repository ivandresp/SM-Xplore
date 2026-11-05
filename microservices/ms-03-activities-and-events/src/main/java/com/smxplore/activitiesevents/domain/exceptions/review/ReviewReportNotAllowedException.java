package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/** Raised when the caller does not own the item the reported review refers to. */
public class ReviewReportNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewReportNotAllowedException(String message) {
        super(message);
    }
}
