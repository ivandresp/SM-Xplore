package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El correo electrónico es obligatorio");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 150 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new ValidationException("El correo electrónico no tiene un formato válido");
        }
    }
}
