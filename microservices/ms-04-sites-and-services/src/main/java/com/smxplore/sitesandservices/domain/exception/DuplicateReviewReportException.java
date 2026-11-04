package com.smxplore.sitesandservices.domain.exception;

import java.util.UUID;

public class DuplicateReviewReportException extends RuntimeException {

    public DuplicateReviewReportException(UUID reviewId, UUID reportedBy) {
        super("Review " + reviewId + " was already reported by user " + reportedBy);
    }
}
