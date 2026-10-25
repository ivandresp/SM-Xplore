package com.smxplore.activitiesevents.infrastructure.adapters.in.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiErrorResponse of(HttpStatus status, String message, ServerWebExchange exchange) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()
        );
    }
}
