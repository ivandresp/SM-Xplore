package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.util.regex.Pattern;

public record Nit(String value) {

    private static final Pattern NIT_PATTERN = Pattern.compile("^[0-9\\-]{6,30}$");

    public Nit {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El NIT es obligatorio");
        }
        value = value.trim();
        if (!NIT_PATTERN.matcher(value).matches()) {
            throw new ValidationException("El NIT no tiene un formato válido");
        }
    }
}
