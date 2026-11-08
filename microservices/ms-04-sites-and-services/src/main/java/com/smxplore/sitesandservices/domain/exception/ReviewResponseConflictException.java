package com.smxplore.sitesandservices.domain.exception;

/**
 * MS04-07: conflicto al responder una reseña que no esta publicada o que ya tiene respuesta.
 */
public class ReviewResponseConflictException extends RuntimeException {

    public ReviewResponseConflictException(String message) {
        super(message);
    }
}
