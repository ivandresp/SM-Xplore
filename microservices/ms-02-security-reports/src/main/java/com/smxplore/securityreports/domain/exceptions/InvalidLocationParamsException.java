package com.smxplore.securityreports.domain.exceptions;

public class InvalidLocationParamsException extends RuntimeException {
    public InvalidLocationParamsException() {
        super("Location needs latitude and longitude information.");
    }
}