package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El hash de la contraseña es obligatorio");
        }
        if (value.length() > 255) {
            throw new ValidationException("El hash de la contraseña no puede superar 255 caracteres");
        }
    }
}
