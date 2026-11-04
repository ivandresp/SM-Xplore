package com.smxplore.sitesandservices.domain.exception;

public class InvalidNotificationEventException extends RuntimeException {

    public InvalidNotificationEventException(String message) {
        super(message);
    }

    public InvalidNotificationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
