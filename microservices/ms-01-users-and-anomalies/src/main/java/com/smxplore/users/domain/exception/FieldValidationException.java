package com.smxplore.users.domain.exception;

import java.util.Map;

/**
 * Regla de negocio que se incumple en un campo concreto del cuerpo.
 *
 * <p>Se renderiza como {@code 400 VALIDATION_ERROR} con el mismo mapa {@code errors} que produce la validación de
 * Bean Validation, para que el cliente marque el campo sin tener que interpretar el texto.
 */
public class FieldValidationException extends DomainException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
