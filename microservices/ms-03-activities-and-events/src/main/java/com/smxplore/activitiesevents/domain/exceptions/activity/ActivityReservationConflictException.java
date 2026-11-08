package com.smxplore.activitiesevents.domain.exceptions.activity;

import java.io.Serial;

/**
 * El cambio de aforo o de franja dejaria reservas confirmadas sin cupo o sin fecha valida. Se
 * responde 409 para que el cliente sepa que debe cancelar o mover las reservas primero.
 */
public class ActivityReservationConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ActivityReservationConflictException(String message) {
        super(message);
    }
}
