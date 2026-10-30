package com.smxplore.users.domain.exception;

public class ReviewAlreadyReportedException extends DomainException {

    public ReviewAlreadyReportedException() {
        super("Ya reportaste esta reseña");
    }
}
