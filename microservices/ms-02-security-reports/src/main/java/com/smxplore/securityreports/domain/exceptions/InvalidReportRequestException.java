package com.smxplore.securityreports.domain.exceptions;

public class InvalidReportRequestException extends RuntimeException {
    public InvalidReportRequestException(String message) {
        super(message);
    }
}
