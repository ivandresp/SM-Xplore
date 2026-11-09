package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/**
 * No se pudo verificar contra ms-04 quien gestiona el sitio del evento reseñado. Se responde 503
 * en lugar de aceptar el snapshot guardado: permitir por defecto dejaria responder a un gestor
 * cesado mientras el catalogo esta caido (fail closed).
 */
public class ReviewResponseUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewResponseUnavailableException(String message) {
        super(message);
    }

    public ReviewResponseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
