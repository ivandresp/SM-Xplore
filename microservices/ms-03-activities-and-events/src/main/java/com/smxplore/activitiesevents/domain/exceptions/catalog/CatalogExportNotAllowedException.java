package com.smxplore.activitiesevents.domain.exceptions.catalog;

import java.io.Serial;

/** El token no tiene el rol ADMIN exigido para el export feed. */
public class CatalogExportNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogExportNotAllowedException(String message) {
        super(message);
    }
}
