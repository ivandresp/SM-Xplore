package com.smxplore.activitiesevents.domain.exceptions.catalog;

import java.io.Serial;

/**
 * El catalogo de sitios (ms-04) no pudo usarse de forma segura: respondio algo que no se puede
 * acotar (mas paginas de las permitidas) o dejo de responder a mitad de la lectura.
 *
 * <p>Se responde {@code 503}: no es culpa del cliente y no se debe asumir nada sobre la propiedad
 * de los sitios cuando la fuente de verdad no esta disponible.
 */
public class SiteCatalogUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SiteCatalogUnavailableException(String message) {
        super(message);
    }

    public SiteCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
