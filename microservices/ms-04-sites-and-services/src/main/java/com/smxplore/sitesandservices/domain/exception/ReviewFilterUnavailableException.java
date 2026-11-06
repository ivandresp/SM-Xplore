package com.smxplore.sitesandservices.domain.exception;

/**
 * El filtro automático de reseñas no está disponible y la política configurada es
 * fail-closed. Maps to HTTP 503.
 */
public class ReviewFilterUnavailableException extends RuntimeException {

    public ReviewFilterUnavailableException() {
        super("The review moderation filter is temporarily unavailable. Please try again later.");
    }
}
