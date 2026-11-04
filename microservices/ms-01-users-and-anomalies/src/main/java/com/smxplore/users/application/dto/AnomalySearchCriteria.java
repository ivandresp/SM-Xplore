package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyStatus;
import com.smxplore.users.domain.model.AnomalyType;
import java.time.Instant;

public record AnomalySearchCriteria(
        AnomalyStatus status,
        AnomalySeverity severity,
        AnomalyType type,
        Instant from,
        Instant to,
        int page,
        int size) {

    public static final int MAX_SIZE = 100;

    public AnomalySearchCriteria {
        if (page < 0) {
            throw new ValidationException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("El rango de fechas no es válido");
        }
    }
}
