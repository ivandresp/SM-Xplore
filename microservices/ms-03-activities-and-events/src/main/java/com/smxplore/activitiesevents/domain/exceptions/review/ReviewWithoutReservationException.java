package com.smxplore.activitiesevents.domain.exceptions.review;

import java.io.Serial;

/**
 * El turista intenta resenar un item que no ha reservado. Solo una reserva CONFIRMED o COMPLETED
 * acredita la experiencia; se responde 403.
 */
public class ReviewWithoutReservationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewWithoutReservationException(String message) {
        super(message);
    }
}
