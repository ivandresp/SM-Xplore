package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;

/**
 * Filtros de la cola de solicitudes. Solo se pueden pedir los roles que pasan por revisión.
 */
public record RegistrationSearchCriteria(Role role, RegistrationStatus status, int page, int size) {

    public static final int MAX_SIZE = 100;
    public static final RegistrationStatus DEFAULT_STATUS = RegistrationStatus.PENDIENTE;

    public RegistrationSearchCriteria {
        if (page < 0) {
            throw new ValidationException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
        if (role != null && role != Role.GUIDE && role != Role.SERVICE_PROVIDER) {
            throw new ValidationException("El rol debe ser GUIDE o SERVICE_PROVIDER");
        }
        // La pantalla es una cola de revisión: por omisión solo interesan las altas pendientes.
        status = status == null ? DEFAULT_STATUS : status;
    }
}
