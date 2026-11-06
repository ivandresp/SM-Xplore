package com.smxplore.users.domain.exception;

public class ReviewNotFoundException extends DomainException {

    public ReviewNotFoundException() {
        super("La reseña no existe");
    }
}
