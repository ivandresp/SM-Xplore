package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.util.regex.Pattern;

public record Phone(String value) {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s]{7,20}$");

    public Phone {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El teléfono es obligatorio");
        }
        value = value.trim();
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new ValidationException("El teléfono no tiene un formato válido");
        }
    }
}
