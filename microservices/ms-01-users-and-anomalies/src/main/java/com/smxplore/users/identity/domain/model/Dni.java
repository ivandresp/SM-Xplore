package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;

public record Dni(String type, String number) {

    public Dni {
        if (type == null || type.isBlank()) {
            throw new ValidationException("El tipo de documento es obligatorio");
        }
        if (number == null || number.isBlank()) {
            throw new ValidationException("El número de documento es obligatorio");
        }
        type = type.trim().toUpperCase();
        number = number.trim();
        if (type.length() > 20 || number.length() > 30) {
            throw new ValidationException("El documento de identidad no es válido");
        }
    }
}
