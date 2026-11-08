package com.smxplore.securityreports.domain.exceptions;

/**
 * Un usuario autenticado intenta comentar un reporte que no es suyo y no es staff (MS02-02).
 */
public class CommentForbiddenException extends RuntimeException {

    public CommentForbiddenException() {
        super("Only the author of the report (or staff) can comment on it.");
    }
}
