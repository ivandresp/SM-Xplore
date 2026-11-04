package com.smxplore.users.domain.exception;

public class ReviewFilterUnavailableException extends DomainException {

    public ReviewFilterUnavailableException() {
        super("El filtro de reseñas no está disponible; vuelve a intentarlo más tarde");
    }
}
