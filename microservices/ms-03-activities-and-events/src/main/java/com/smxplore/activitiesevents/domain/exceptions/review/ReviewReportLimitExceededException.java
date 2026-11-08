package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/**
 * Se lanza cuando un usuario supera el numero de denuncias permitidas en la ventana de tiempo. El
 * limite es por cuenta (no por IP) y protege la cola de moderacion de un unico denunciante.
 */
public class ReviewReportLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewReportLimitExceededException(String message) {
        super(message);
    }
}
