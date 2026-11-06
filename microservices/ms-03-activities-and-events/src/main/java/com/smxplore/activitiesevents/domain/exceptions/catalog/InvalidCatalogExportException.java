package com.smxplore.activitiesevents.domain.exceptions.catalog;

import java.io.Serial;

/** El pedido de export llega con parámetros que no se pueden atender (entidad, página o tamaño). */
public class InvalidCatalogExportException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCatalogExportException(String message) {
        super(message);
    }
}
