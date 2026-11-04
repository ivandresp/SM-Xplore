package com.smxplore.users.domain.exception;

public class DuplicateReviewException extends DomainException {

    public DuplicateReviewException() {
        super("Ya existe una reseña de este turista para el mismo destino");
    }
}
