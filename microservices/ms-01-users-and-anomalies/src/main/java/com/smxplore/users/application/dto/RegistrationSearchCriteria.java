package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;

/**
 * Filtros de la cola de solicitudes. Solo se pueden pedir los roles que pasan por revisión y solo los estados que
 * todavía requieren una decisión.
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
        if (role != null && role != Role.GUIDE && role != Role.SERVICE_PROVIDER && role != Role.DESTINATION_MANAGER) {
            throw new ValidationException("El rol debe ser GUIDE, SERVICE_PROVIDER o DESTINATION_MANAGER");
        }
        // La cola es de trabajo, no un directorio: los altas ya aprobadas exponen nombre, correo, licencia y NIT de
        // toda la plataforma, así que solo se consultan aquí las que aún esperan decisión. El listado de aprobadas
        // sigue viviendo en /api/v1/admin/users, que es solo de ADMIN.
        if (status == RegistrationStatus.APROBADO) {
            throw new ValidationException("La cola no consulta altas APROBADAS; usa /api/v1/admin/users como ADMIN");
        }
        status = status == null ? DEFAULT_STATUS : status;
    }
}
