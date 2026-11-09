package com.smxplore.activitiesevents.domain.exceptions.catalog;

import java.io.Serial;

/**
 * Filtro o ventana de paginación inválidos en los listados públicos del catálogo.
 * Se responde 400 con el código {@code INVALID_FILTER} para no exponer detalles internos.
 */
public class InvalidCatalogFilterException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCatalogFilterException(String message) {
        super(message);
    }
}
