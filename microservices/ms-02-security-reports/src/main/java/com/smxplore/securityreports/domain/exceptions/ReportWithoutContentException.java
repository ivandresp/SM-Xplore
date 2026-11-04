package com.smxplore.securityreports.domain.exceptions;

public class ReportWithoutContentException extends RuntimeException {
    public ReportWithoutContentException(String message) {
        super(message);
    }
}
