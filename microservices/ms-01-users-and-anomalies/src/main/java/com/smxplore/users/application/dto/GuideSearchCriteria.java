package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;

/**
 * Filtros del listado de guías para revisión. Si no se indica estado se listan las pendientes.
 */
public record GuideSearchCriteria(RegistrationStatus registrationStatus, int page, int size) {

    public static final int MAX_SIZE = 100;

    public GuideSearchCriteria {
        registrationStatus = registrationStatus == null ? RegistrationStatus.PENDIENTE : registrationStatus;
        if (page < 0) {
            throw new ValidationException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
    }
}
