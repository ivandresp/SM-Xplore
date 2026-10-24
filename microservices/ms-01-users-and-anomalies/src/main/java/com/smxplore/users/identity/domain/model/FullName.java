package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;

public record FullName(String value) {

    public FullName {
        if (value == null || value.isBlank()) {
            throw new ValidationException("El nombre completo es obligatorio");
        }
        value = value.trim().replaceAll("\\s+", " ");
        if (value.length() > 150) {
            throw new ValidationException("El nombre completo no puede superar 150 caracteres");
        }
    }
}
